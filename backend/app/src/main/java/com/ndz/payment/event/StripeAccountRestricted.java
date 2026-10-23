package com.ndz.payment.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Connected account của organizer chuyển sang trạng thái {@code restricted} (overview.md §7.6): Stripe đã nhận đủ hồ
 * sơ nhưng không cho nhận tiền. Notification báo Admin; chỉ phát khi trước đó chưa ở trạng thái này.
 * {@code organizerName} để Notification không phải hỏi Catalog; {@code disabledReason} là mã của Stripe (vd. {@code rejected.fraud}).
 */
public record StripeAccountRestricted(UUID messageId, Instant occurredAt, UUID organizerId, String organizerName,
		String disabledReason)
		implements IntegrationEvent {
}
