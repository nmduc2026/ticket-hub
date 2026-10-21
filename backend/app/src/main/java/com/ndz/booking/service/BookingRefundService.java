package com.ndz.booking.service;

import java.util.UUID;

import com.ndz.booking.dto.response.BookingViews.RefundResult;
import com.ndz.booking.event.TicketRevoked;

/** Hoàn vé phía Booking (overview.md §7.4, ADR 0026): nhận yêu cầu của khách và nhả ghế sau khi vé đã thu hồi. */
public interface BookingRefundService {

	/**
	 * Khách yêu cầu hoàn cả booking (BR-07): booking {@code CONFIRMED}, còn ít nhất {@code refundCutoff} trước giờ diễn
	 * → {@code REFUND_REQUESTED} và phát {@code RefundRequested}. Booking của người khác coi như không tồn tại.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND}, {@code BOOKING_NOT_REFUNDABLE}
	 *         hoặc {@code REFUND_WINDOW_CLOSED}
	 */
	RefundResult requestRefund(UUID userId, UUID bookingId);

	/** Đếm vé đã thu hồi; đủ vé của booking thì nhả ghế về {@code AVAILABLE}. Idempotent. */
	void onTicketRevoked(TicketRevoked event);

}
