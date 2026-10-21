package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Stripe đã hoàn tiền xong (overview.md §7.4): Booking chuyển {@code REFUND_REQUESTED → REFUNDED}, Notification gửi
 * email xác nhận. {@code userId}, {@code contactEmail}, {@code eventTitle} có khi hoàn theo yêu cầu hoàn vé; hoàn tự
 * động vì thanh toán đến muộn thì để trống. Khai báo ở module Booking vì Booking là bên nhận (xem
 * {@link PaymentSucceeded}).
 */
public record RefundCompleted(UUID messageId, Instant occurredAt, UUID refundId, UUID paymentId, UUID bookingId,
		UUID userId, long amountMinor, String currency, String contactEmail, String eventTitle)
		implements IntegrationEvent {
}
