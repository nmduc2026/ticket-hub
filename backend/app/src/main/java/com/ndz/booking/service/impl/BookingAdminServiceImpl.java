package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.response.BookingViews.AdminBookingSummary;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.RefundContext;
import com.ndz.booking.dto.response.BookingViews.RefundResult;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.service.BookingAdminService;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
public class BookingAdminServiceImpl implements BookingAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final Logger log = LoggerFactory.getLogger(BookingAdminServiceImpl.class);

	private final BookingRepository bookings;
	private final ApplicationEventPublisher publisher;
	private final AuditLogService audit;
	private final Clock clock;

	BookingAdminServiceImpl(BookingRepository bookings, ApplicationEventPublisher publisher, AuditLogService audit,
			Clock clock) {
		this.bookings = bookings;
		this.publisher = publisher;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminBookingSummary> listByStatus(BookingStatus status, int page, int size) {
		int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return bookings.findByStatus(status, limit, Math.max(page, 0) * limit);
	}

	@Override
	@Transactional
	public RefundResult retryRefund(UUID adminId, UUID bookingId) {
		LockedBooking booking = bookings.lockBooking(bookingId).orElseThrow(
				() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy đơn đặt vé"));
		if (booking.status() != BookingStatus.REFUND_FAILED) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_REFUNDABLE, "Chỉ hoàn thủ công được đơn hoàn thất bại");
		}
		booking.status().requireTransitionTo(BookingStatus.REFUND_REQUESTED);
		Instant now = clock.instant();
		if (!bookings.markRefundRetried(bookingId, now)) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_REFUNDABLE, "Chỉ hoàn thủ công được đơn hoàn thất bại");
		}
		RefundContext context = bookings.refundContext(bookingId);
		audit.record(adminId, AuditAction.REFUND_RETRIED, "booking", bookingId,
				Map.of("status", BookingStatus.REFUND_FAILED.name()),
				Map.of("status", BookingStatus.REFUND_REQUESTED.name(), "amountMinor", context.amountMinor()));
		ConfirmationData data = bookings.confirmationData(bookingId);
		publisher.publishEvent(new RefundRequested(Uuid7.next(clock), now, bookingId, booking.userId(),
				booking.eventId(), data.eventTitle(), data.contactEmail(), context.reason(), context.amountMinor(),
				data.currency(), null));
		log.warn("Admin hoàn thủ công booking {} ({}, {})", bookingId, context.reason(), context.amountMinor());
		return new RefundResult(bookingId, BookingStatus.REFUND_REQUESTED, context.amountMinor());
	}

}
