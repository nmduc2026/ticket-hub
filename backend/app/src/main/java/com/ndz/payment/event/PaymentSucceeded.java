package com.ndz.payment.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Stripe báo thanh toán thành công (hoặc đối soát thấy PaymentIntent đã {@code succeeded}); Booking xác nhận đơn. */
public record PaymentSucceeded(UUID messageId, Instant occurredAt, UUID paymentId, UUID bookingId, long amountMinor,
		String currency) implements IntegrationEvent {
}
