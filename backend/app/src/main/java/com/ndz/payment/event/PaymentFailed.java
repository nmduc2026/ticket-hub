package com.ndz.payment.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Một lần thanh toán thất bại (vd. thẻ bị từ chối). Không phải trạng thái kết thúc: payment vẫn dùng lại được để khách
 * thử thẻ khác trong hạn giữ ghế (overview.md §7.3). {@code failureCode} là mã của Stripe, có thể null.
 */
public record PaymentFailed(UUID messageId, Instant occurredAt, UUID paymentId, UUID bookingId, String failureCode)
		implements IntegrationEvent {
}
