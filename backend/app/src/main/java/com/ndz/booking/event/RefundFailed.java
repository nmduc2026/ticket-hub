package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Lần hoàn tiền thất bại (overview.md §7.4, ADR 0026): Booking chuyển {@code REFUND_REQUESTED → REFUND_FAILED} để Admin
 * hoàn thủ công; vé vẫn {@code REVOKED}. {@code refundId} trống khi không tạo được lần hoàn nào (payment không hoàn
 * được). {@code failureCode} là mã của Stripe hoặc {@code PAYMENT_NOT_REFUNDABLE}.
 */
public record RefundFailed(UUID messageId, Instant occurredAt, UUID refundId, UUID paymentId, UUID bookingId,
		UUID userId, long amountMinor, String currency, String failureCode, String eventTitle)
		implements IntegrationEvent {

	public static final String PAYMENT_NOT_REFUNDABLE = "PAYMENT_NOT_REFUNDABLE";

}
