package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.config.BookingProperties;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.PaymentInfo;
import com.ndz.booking.dto.response.BookingViews.RefundResult;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.entity.enums.LatePayment;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.event.TicketIssuanceFailed;
import com.ndz.booking.event.TicketRevoked;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.service.BookingRefundService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.PlatformFee;
import com.ndz.shared.util.Uuid7;

@Service
public class BookingRefundServiceImpl implements BookingRefundService {

	static final String CONSUMER = "booking.ticket-revoked";
	static final String ISSUANCE_FAILED_CONSUMER = "booking.ticket-issuance-failed";
	static final String COMPLETED_CONSUMER = "booking.refund-completed";
	static final String FAILED_CONSUMER = "booking.refund-failed";

	private static final Logger log = LoggerFactory.getLogger(BookingRefundServiceImpl.class);

	private final BookingRepository bookings;
	private final ProcessedEventRepository processed;
	private final BookingProperties properties;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	BookingRefundServiceImpl(BookingRepository bookings, ProcessedEventRepository processed,
			BookingProperties properties, ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
		this.processed = processed;
		this.properties = properties;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	@Transactional
	public RefundResult requestRefund(UUID userId, UUID bookingId) {
		// Khoá dòng booking: yêu cầu hoàn đồng thời, hay hoàn đồng thời với huỷ / hết hạn, chỉ một bên thắng.
		LockedBooking booking = bookings.lockBooking(bookingId).filter(found -> found.userId().equals(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy đơn đặt vé"));
		if (booking.status() != BookingStatus.CONFIRMED) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_REFUNDABLE, "Chỉ hoàn được đơn đã thanh toán thành công");
		}
		ConfirmationData data = bookings.confirmationData(bookingId);
		Instant now = clock.instant();
		// BR-07: còn >= 48 giờ trước giờ diễn (đúng 48 giờ vẫn được).
		if (now.isAfter(data.startsAt().minus(properties.refundCutoff()))) {
			throw new BusinessException(ErrorCode.REFUND_WINDOW_CLOSED,
					"Chỉ hoàn vé khi còn ít nhất " + properties.refundCutoff().toHours() + " giờ trước giờ diễn");
		}
		PaymentInfo info = bookings.paymentInfo(bookingId);
		// BR-07: khách nhận lại số đã trả trừ phí nền tảng (BR-06).
		long refundAmount = data.totalMinor() - PlatformFee.of(data.totalMinor(), info.platformFeeBps());

		booking.status().requireTransitionTo(BookingStatus.REFUND_REQUESTED);
		if (!bookings.markRefundRequested(bookingId, RefundRequested.CUSTOMER_REQUEST, refundAmount, now)) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_REFUNDABLE, "Chỉ hoàn được đơn đã thanh toán thành công");
		}
		publisher.publishEvent(new RefundRequested(Uuid7.next(clock), now, bookingId, userId, data.eventId(),
				data.eventTitle(), data.contactEmail(), RefundRequested.CUSTOMER_REQUEST, refundAmount,
				data.currency()));
		log.info("Booking {} yêu cầu hoàn {} {}", bookingId, refundAmount, data.currency());
		return new RefundResult(bookingId, BookingStatus.REFUND_REQUESTED, refundAmount);
	}

	@Override
	public void onTicketIssuanceFailed(TicketIssuanceFailed event) {
		if (!processed.markProcessed(ISSUANCE_FAILED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua TicketIssuanceFailed đã xử lý {}", event.messageId());
			return;
		}
		LockedBooking booking = bookings.lockBooking(event.bookingId()).orElse(null);
		if (booking == null || booking.status() != BookingStatus.CONFIRMED) {
			// Không đoán: có tiền, không vé, booking không ở trạng thái hoàn được. Cần người xem (§16.3).
			log.error("TicketIssuanceFailed cho booking {} đang {}: không chuyển sang hoàn được", event.bookingId(),
					booking == null ? "không tồn tại" : booking.status());
			return;
		}
		Instant now = clock.instant();
		booking.status().requireTransitionTo(BookingStatus.REFUND_REQUESTED);
		if (!bookings.markRefundRequested(event.bookingId(), RefundRequested.TICKET_ISSUANCE_FAILED,
				event.totalMinor(), now)) {
			throw new IllegalStateException("Booking " + event.bookingId() + " đã bị chuyển trạng thái khi đang khoá");
		}
		publisher.publishEvent(new RefundRequested(Uuid7.next(clock), now, event.bookingId(), event.userId(),
				event.eventId(), event.eventTitle(), event.contactEmail(), RefundRequested.TICKET_ISSUANCE_FAILED,
				event.totalMinor(), event.currency()));
		log.error("Booking {} chuyển REFUND_REQUESTED vì không phát được vé (ghế trùng {}): hoàn 100%",
				event.bookingId(), event.conflictingSeatIds());
	}

	@Override
	public void onRefundCompleted(RefundCompleted event) {
		if (!processed.markProcessed(COMPLETED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundCompleted đã xử lý {}", event.messageId());
			return;
		}
		moveAfterRefund(event.bookingId(), BookingStatus.REFUNDED, "RefundCompleted " + event.refundId(), event.occurredAt());
	}

	@Override
	public void onRefundFailed(RefundFailed event) {
		if (!processed.markProcessed(FAILED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundFailed đã xử lý {}", event.messageId());
			return;
		}
		moveAfterRefund(event.bookingId(), BookingStatus.REFUND_FAILED,
				"RefundFailed " + event.refundId() + " (" + event.failureCode() + ")", event.occurredAt());
	}

	/**
	 * Hoàn tự động của thanh toán đến muộn (§7.3): booking giữ nguyên {@code EXPIRED} / {@code CANCELLED}, chỉ ghi kết
	 * quả hoàn vào {@code late_payment} để khách thấy.
	 */
	private void recordLatePaymentRefund(UUID bookingId, BookingStatus outcome, String source) {
		LatePayment latePayment = outcome == BookingStatus.REFUNDED ? LatePayment.REFUNDED : LatePayment.REFUND_FAILED;
		if (bookings.markLatePayment(bookingId, latePayment)) {
			log.info("{}: thanh toán đến muộn của booking {} → {}", source, bookingId, latePayment);
		}
		else {
			log.info("{}: booking {} không có thanh toán đến muộn, không đổi", source, bookingId);
		}
	}

	/**
	 * Booking đang {@code REFUND_REQUESTED} chuyển theo kết quả hoàn vé; booking {@code EXPIRED} / {@code CANCELLED}
	 * (hoàn tự động vì thanh toán đến muộn) chỉ ghi {@code late_payment}; trạng thái khác bỏ qua.
	 */
	private void moveAfterRefund(UUID bookingId, BookingStatus next, String source, Instant occurredAt) {
		LockedBooking booking = bookings.lockBooking(bookingId).orElse(null);
		if (booking == null) {
			log.error("{} cho booking không tồn tại {}", source, bookingId);
			return;
		}
		if (booking.status() == BookingStatus.EXPIRED || booking.status() == BookingStatus.CANCELLED) {
			recordLatePaymentRefund(bookingId, next, source);
			return;
		}
		if (booking.status() != BookingStatus.REFUND_REQUESTED) {
			log.info("{}: booking {} đang {}, không đổi", source, bookingId, booking.status());
			return;
		}
		booking.status().requireTransitionTo(next);
		if (!bookings.transition(bookingId, BookingStatus.REFUND_REQUESTED, next)) {
			throw new IllegalStateException("Booking " + bookingId + " đã bị chuyển trạng thái khi đang khoá");
		}
		if (next == BookingStatus.REFUND_FAILED) {
			bookings.markRefundFailedAt(bookingId, occurredAt);
			// Alert mức cao (overview.md §16.3): cần Admin hoàn thủ công.
			log.error("Booking {} chuyển REFUND_FAILED ({})", bookingId, source);
			return;
		}
		log.info("Booking {} chuyển {} ({})", bookingId, next, source);
	}

	@Override
	public void onTicketRevoked(TicketRevoked event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua TicketRevoked đã xử lý {}", event.messageId());
			return;
		}
		// Khoá booking để hai TicketRevoked đồng thời không cùng thấy "chưa đủ" rồi cùng bỏ lỡ lần nhả ghế.
		if (bookings.lockBooking(event.bookingId()).isEmpty()) {
			log.error("TicketRevoked {} cho booking không tồn tại {}", event.ticketId(), event.bookingId());
			return;
		}
		bookings.recordRevokedTicket(event.bookingId(), event.ticketId(), clock.instant());
		int revoked = bookings.revokedTicketCount(event.bookingId());
		int expected = bookings.itemCount(event.bookingId());
		if (revoked < expected) {
			log.info("Booking {}: đã thu hồi {}/{} vé, chưa nhả ghế", event.bookingId(), revoked, expected);
			return;
		}
		// ADR 0026: chỉ nhả ghế khi mọi vé cũ đã thu hồi, để vé của người mua sau không vi phạm UNIQUE.
		List<UUID> released = bookings.releaseSoldSeats(event.bookingId());
		log.info("Booking {}: mọi vé đã thu hồi, nhả {} ghế", event.bookingId(), released.size());
	}

}
