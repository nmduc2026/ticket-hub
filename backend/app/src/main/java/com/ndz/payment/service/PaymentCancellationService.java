package com.ndz.payment.service;

import java.util.UUID;

/** Huỷ PaymentIntent khi booking đã hết hạn hoặc bị huỷ (overview.md §7.3, BR-12). */
public interface PaymentCancellationService {

	/**
	 * Booking đã đóng ({@code BookingExpired} / {@code BookingCancelled}): huỷ PaymentIntent của nó nếu có. Huỷ thất bại
	 * vì PaymentIntent đã {@code succeeded} thì lấy lại trạng thái từ Stripe và xử lý như {@code PaymentSucceeded}.
	 * Idempotent theo {@code messageId}. Người gọi (listener) mở transaction.
	 */
	void onBookingClosed(UUID messageId, UUID bookingId);

}
