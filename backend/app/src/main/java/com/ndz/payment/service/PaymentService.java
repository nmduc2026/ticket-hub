package com.ndz.payment.service;

import java.util.UUID;

import com.ndz.payment.dto.response.PaymentResponse;

/** Thanh toán một booking bằng Stripe (overview.md §7.2). */
public interface PaymentService {

	/**
	 * Bắt đầu thanh toán: Booking kiểm chủ sở hữu và hạn giữ ghế (BR-03), rồi tạo PaymentIntent với idempotency key
	 * {@code booking-{bookingId}}. Gọi lại cho cùng booking trả cùng payment và cùng {@code clientSecret}; PaymentIntent
	 * chỉ được tạo một lần.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND}, {@code BOOKING_NOT_PAYABLE},
	 *         {@code HOLD_EXPIRING} hoặc {@code PAYMENT_PROVIDER_UNAVAILABLE}
	 */
	PaymentResponse startPayment(UUID userId, UUID bookingId);

}
