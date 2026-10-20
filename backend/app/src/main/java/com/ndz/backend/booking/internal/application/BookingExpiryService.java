package com.ndz.backend.booking.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.backend.booking.events.BookingExpired;
import com.ndz.backend.booking.internal.domain.BookingStatus;
import com.ndz.backend.booking.internal.domain.BookingViews.LockedBooking;
import com.ndz.backend.booking.internal.persistence.BookingRepository;
import com.ndz.backend.shared.Uuid7;

/**
 * Hết hạn giữ ghế (BR-03; overview.md §7.3). Hạn tính hoàn toàn theo Postgres: {@code expires_at}, hoặc
 * {@code expires_at + ân hạn} nếu đã bắt đầu thanh toán. Mỗi booking xử lý trong transaction riêng: khoá dòng, kiểm
 * lại điều kiện, chuyển {@code PENDING_PAYMENT → EXPIRED}, nhả ghế, phát {@code BookingExpired}. Chạy lại nhiều lần
 * hay chạy đồng thời với việc khách huỷ đều an toàn vì mọi chuyển có điều kiện trạng thái cũ.
 */
@Service
public class BookingExpiryService {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

	private final BookingRepository bookings;
	private final BookingProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	BookingExpiryService(BookingRepository bookings, BookingProperties properties, TransactionTemplate transaction,
			ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
		this.properties = properties;
		this.transaction = transaction;
		this.publisher = publisher;
		this.clock = clock;
	}

	/**
	 * Hết hạn mọi booking đã quá hạn, theo lô.
	 *
	 * @return số booking đã chuyển sang {@code EXPIRED}
	 */
	public int expireDueBookings() {
		int expired = 0;
		while (true) {
			Instant now = clock.instant();
			List<UUID> due = bookings.findDueForExpiry(now, properties.paymentGrace(), properties.expiryBatchSize());
			int expiredInBatch = 0;
			for (UUID bookingId : due) {
				if (Boolean.TRUE.equals(transaction.execute(status -> expireOne(bookingId, now)))) {
					expiredInBatch++;
				}
			}
			expired += expiredInBatch;
			// Lô chưa đầy thì đã hết việc; lô không có booking nào đổi được (vừa được thanh toán / huỷ) thì dừng để khỏi lặp.
			if (due.size() < properties.expiryBatchSize() || expiredInBatch == 0) {
				return expired;
			}
		}
	}

	private boolean expireOne(UUID bookingId, Instant now) {
		LockedBooking booking = bookings.lockBooking(bookingId).orElse(null);
		if (booking == null || booking.status() != BookingStatus.PENDING_PAYMENT || now.isBefore(deadline(booking))) {
			// Booking vừa được thanh toán, huỷ, hoặc vừa bắt đầu thanh toán (ân hạn): không hết hạn lúc này.
			return false;
		}
		booking.status().requireTransitionTo(BookingStatus.EXPIRED);
		if (!bookings.transition(bookingId, BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED)) {
			return false;
		}
		List<UUID> seatIds = bookings.releaseSeats(bookingId);
		publisher.publishEvent(new BookingExpired(Uuid7.next(clock), now, bookingId, booking.userId(),
				booking.eventId(), seatIds));
		log.info("Booking {} hết hạn, nhả {} ghế", bookingId, seatIds.size());
		return true;
	}

	private Instant deadline(LockedBooking booking) {
		return booking.paymentStartedAt() == null ? booking.expiresAt()
				: booking.expiresAt().plus(properties.paymentGrace());
	}

}
