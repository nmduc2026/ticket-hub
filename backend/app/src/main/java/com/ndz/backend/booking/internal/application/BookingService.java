package com.ndz.backend.booking.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.ndz.backend.booking.events.BookingCreated;
import com.ndz.backend.booking.internal.domain.BookingViews.BookingView;
import com.ndz.backend.booking.internal.domain.BookingViews.EventRules;
import com.ndz.backend.booking.internal.domain.BookingViews.LockedSeat;
import com.ndz.backend.booking.internal.persistence.BookingRepository;
import com.ndz.backend.booking.internal.persistence.EventSnapshotRepository;
import com.ndz.backend.shared.BusinessException;
import com.ndz.backend.shared.Uuid7;

/**
 * Giữ ghế (overview.md §7.2 đến bước 201, §10 lớp 2). Quy tắc kiểm trước khi khoá ghế (BR-02, sự kiện đang bán); phần
 * khoá và giữ ghế nằm trong một transaction ngắn: khoá theo thứ tự {@code seat_id}, cập nhật có điều kiện
 * {@code status = 'AVAILABLE'}, so số dòng với số ghế yêu cầu (BR-01). Redis (lớp 1) thêm ở Sprint 5.
 */
@Service
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	/** Deadlock (40P01, Spring dịch thành PessimisticLockingFailureException) thử lại một lần; vẫn lỗi thì coi như ghế đang bị tranh (overview.md §10). */
	private static final int MAX_ATTEMPTS = 2;

	private static final String ONE_PENDING_INDEX = "uq_one_pending_per_user";

	private final BookingRepository bookings;
	private final EventSnapshotRepository snapshots;
	private final BookingProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	BookingService(BookingRepository bookings, EventSnapshotRepository snapshots, BookingProperties properties,
			TransactionTemplate transaction, ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
		this.snapshots = snapshots;
		this.properties = properties;
		this.transaction = transaction;
		this.publisher = publisher;
		this.clock = clock;
	}

	/**
	 * Giữ ghế cho một người dùng.
	 *
	 * @throws BusinessException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_ON_SALE}, {@code MAX_SEATS_EXCEEDED},
	 *         {@code DUPLICATE_SEATS}, {@code PENDING_BOOKING_EXISTS}, {@code TICKET_LIMIT_EXCEEDED},
	 *         {@code SEAT_UNAVAILABLE}
	 */
	public BookingView hold(UUID userId, String contactEmail, UUID eventId, List<UUID> seatIds) {
		validateRequest(contactEmail, seatIds);
		EventRules rules = snapshots.rules(eventId).orElseThrow(
				() -> new BusinessException("EVENT_NOT_FOUND", "Không tìm thấy sự kiện", HttpStatus.NOT_FOUND));
		if (!"ON_SALE".equals(rules.status())) {
			throw new BusinessException("EVENT_NOT_ON_SALE", "Sự kiện chưa mở bán hoặc đã dừng bán",
					HttpStatus.CONFLICT);
		}
		checkUserLimits(userId, rules, seatIds.size());

		for (int attempt = 1;; attempt++) {
			try {
				return transaction.execute(status -> holdInTransaction(userId, contactEmail, eventId, seatIds));
			}
			catch (PessimisticLockingFailureException e) {
				if (attempt >= MAX_ATTEMPTS) {
					log.warn("Deadlock khi giữ ghế sự kiện {} sau {} lần thử", eventId, attempt);
					throw seatUnavailable();
				}
				log.info("Deadlock khi giữ ghế sự kiện {}, thử lại", eventId);
			}
			catch (DuplicateKeyException e) {
				// Hai request cùng lúc của một người: chỉ bản ghi đầu tiên qua được unique index (BR-02).
				if (e.getMessage() != null && e.getMessage().contains(ONE_PENDING_INDEX)) {
					throw pendingExists();
				}
				throw e;
			}
		}
	}

	@Transactional(readOnly = true)
	public BookingView get(UUID userId, UUID bookingId) {
		return bookings.findOwned(bookingId, userId).orElseThrow(
				() -> new BusinessException("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND));
	}

	private void validateRequest(String contactEmail, List<UUID> seatIds) {
		if (!StringUtils.hasText(contactEmail)) {
			throw new BusinessException("EMAIL_REQUIRED", "Tài khoản chưa có email để gửi vé");
		}
		if (seatIds.size() > properties.maxSeatsPerBooking()) {
			throw new BusinessException("MAX_SEATS_EXCEEDED",
					"Mỗi lần đặt tối đa " + properties.maxSeatsPerBooking() + " ghế");
		}
		if (new HashSet<>(seatIds).size() != seatIds.size()) {
			throw new BusinessException("DUPLICATE_SEATS", "Danh sách ghế có ghế bị lặp");
		}
	}

	/** BR-02: một booking chờ thanh toán mỗi sự kiện; tổng vé (chờ thanh toán + đã xác nhận) không vượt giới hạn. */
	private void checkUserLimits(UUID userId, EventRules rules, int requested) {
		if (bookings.hasPendingBooking(userId, rules.eventId())) {
			throw pendingExists();
		}
		if (bookings.activeTicketCount(userId, rules.eventId()) + requested > rules.maxTicketsPerUser()) {
			throw new BusinessException("TICKET_LIMIT_EXCEEDED",
					"Mỗi người mua tối đa " + rules.maxTicketsPerUser() + " vé cho sự kiện này");
		}
	}

	private BookingView holdInTransaction(UUID userId, String contactEmail, UUID eventId, List<UUID> seatIds) {
		List<LockedSeat> seats = bookings.lockSeats(eventId, seatIds);
		boolean allAvailable = seats.size() == seatIds.size()
				&& seats.stream().allMatch(seat -> "AVAILABLE".equals(seat.status()));
		if (!allAvailable) {
			// Có ghế không tồn tại trong tồn kho, đã giữ hoặc đã bán: không giữ ghế nào.
			throw seatUnavailable();
		}

		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.holdDuration());
		long total = seats.stream().mapToLong(LockedSeat::priceMinor).sum();
		String currency = seats.get(0).currency();
		UUID bookingId = Uuid7.next(clock);
		bookings.insertBooking(bookingId, userId, eventId, total, currency, expiresAt, contactEmail, now);
		bookings.insertItems(bookingId, eventId, seats);
		if (bookings.markHeld(eventId, seatIds, bookingId) != seatIds.size()) {
			// Bất biến BR-01: số dòng đổi được phải đúng bằng số ghế yêu cầu, nếu không hoàn tác toàn bộ.
			throw seatUnavailable();
		}
		publisher.publishEvent(new BookingCreated(Uuid7.next(clock), now, bookingId, userId, eventId, seatIds, total,
				currency, expiresAt, contactEmail));
		return bookings.findOwned(bookingId, userId).orElseThrow();
	}

	private static BusinessException seatUnavailable() {
		return new BusinessException("SEAT_UNAVAILABLE", "Ghế vừa có người giữ, vui lòng chọn ghế khác",
				HttpStatus.CONFLICT);
	}

	private static BusinessException pendingExists() {
		return new BusinessException("PENDING_BOOKING_EXISTS",
				"Bạn đang có một đơn chờ thanh toán cho sự kiện này", HttpStatus.CONFLICT);
	}

}
