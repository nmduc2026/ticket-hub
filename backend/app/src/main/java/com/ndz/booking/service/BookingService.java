package com.ndz.booking.service;

import java.util.List;
import java.util.UUID;

import com.ndz.booking.dto.response.BookingViews.BookingView;

/**
 * Giữ ghế và xem / huỷ đơn của chính mình (overview.md §7.2, §10). Quy tắc kiểm trước khi khoá ghế (BR-02, sự kiện
 * đang bán); phần khoá và giữ ghế nằm trong một transaction ngắn (BR-01).
 */
public interface BookingService {

	/**
	 * Giữ ghế cho một người dùng.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_ON_SALE},
	 *         {@code MAX_SEATS_EXCEEDED}, {@code DUPLICATE_SEATS}, {@code PENDING_BOOKING_EXISTS},
	 *         {@code TICKET_LIMIT_EXCEEDED}, {@code SEAT_UNAVAILABLE}
	 */
	BookingView hold(UUID userId, String contactEmail, UUID eventId, List<UUID> seatIds);

	/**
	 * Khách tự huỷ booking đang chờ thanh toán để nhả ghế ngay (BR-12); booking của người khác coi như không tồn tại.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND} hoặc
	 *         {@code BOOKING_NOT_CANCELLABLE}
	 */
	void cancel(UUID userId, UUID bookingId);

	BookingView get(UUID userId, UUID bookingId);

}
