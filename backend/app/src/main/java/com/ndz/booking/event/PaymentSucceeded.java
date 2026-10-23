package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Stripe báo thanh toán thành công (hoặc đối soát thấy PaymentIntent đã {@code succeeded}); Booking xác nhận đơn. Khai báo
 * ở module Booking (bên nhận định nghĩa điều nó cần) để Booking không phụ thuộc ngược vào Payment, tránh vòng giữa hai
 * module; Payment phát event này.
 */
public record PaymentSucceeded(UUID messageId, Instant occurredAt, UUID paymentId, UUID bookingId, long amountMinor,
		String currency) implements IntegrationEvent {
}
