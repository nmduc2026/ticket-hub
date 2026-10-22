package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.api.BookingApi;
import com.ndz.booking.config.BookingProperties;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.PaymentInfo;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.service.SeatHoldService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/** Cài đặt {@link BookingApi} trong cùng process (giai đoạn modular monolith). */
@Service
public class BookingApiImpl implements BookingApi {

	private final BookingRepository bookings;
	private final SeatHoldService seatHolds;
	private final BookingProperties properties;
	private final Clock clock;

	BookingApiImpl(BookingRepository bookings, SeatHoldService seatHolds, BookingProperties properties,
			Clock clock) {
		this.bookings = bookings;
		this.seatHolds = seatHolds;
		this.properties = properties;
		this.clock = clock;
	}

	/** Khoá dòng booking nên không xung đột với job hết hạn hay huỷ cùng lúc. */
	@Override
	@Transactional
	public PaymentStart startPayment(UUID userId, UUID bookingId) {
		LockedBooking booking = bookings.lockBooking(bookingId).filter(found -> found.userId().equals(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy đơn đặt vé"));
		if (booking.status() != BookingStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_PAYABLE, "Đơn không còn chờ thanh toán");
		}
		Instant now = clock.instant();
		if (booking.paymentStartedAt() == null) {
			// BR-03: còn dưới 90 giây thì không bắt đầu thanh toán để tránh hết hạn giữa lúc khách nhập thẻ.
			if (Duration.between(now, booking.expiresAt()).compareTo(properties.paymentStartMinRemaining()) < 0) {
				throw holdExpiring();
			}
			bookings.markPaymentStarted(bookingId, now);
			// BR-03: từ đây booking được ân hạn thêm, key Redis phải sống lâu bằng hạn Postgres mới.
			seatHolds.extend(booking.eventId(), bookings.seatIdsOf(bookingId), bookingId,
					booking.expiresAt().plus(properties.paymentGrace()));
		}
		else if (!now.isBefore(booking.expiresAt().plus(properties.paymentGrace()))) {
			// Đã qua cả ân hạn: job hết hạn sắp chuyển EXPIRED.
			throw holdExpiring();
		}
		PaymentInfo info = bookings.paymentInfo(bookingId);
		return new PaymentStart(bookingId, info.totalMinor(), info.currency(), info.platformFeeBps(),
				info.organizerId());
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserBooking> bookingsOf(UUID userId, int limit) {
		return bookings.findByUser(userId, limit, 0, properties.refundCutoff()).stream()
				.map(summary -> new UserBooking(summary.bookingId(), summary.eventTitle(), summary.startsAt(),
						summary.status(), summary.totalMinor(), summary.currency(), summary.createdAt(),
						summary.refundStatus(), summary.refundAmountMinor(), summary.ticketCount()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> notConfirmed(Collection<UUID> bookingIds) {
		if (bookingIds.isEmpty()) {
			return Set.of();
		}
		Set<UUID> confirmed = bookings.findConfirmed(bookingIds);
		return bookingIds.stream().filter(id -> !confirmed.contains(id)).collect(Collectors.toSet());
	}

	private static BusinessException holdExpiring() {
		return new BusinessException(ErrorCode.HOLD_EXPIRING,
				"Sắp hết thời gian giữ ghế, vui lòng chọn lại ghế");
	}

}
