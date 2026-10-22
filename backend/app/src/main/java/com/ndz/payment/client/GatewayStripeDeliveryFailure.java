package com.ndz.payment.client;

import java.time.Instant;

/** Event Stripe đã thử gửi webhook nhưng không nhận được xác nhận (Admin màn #36). */
public record GatewayStripeDeliveryFailure(String id, String type, Instant createdAt, int pendingWebhooks) {
}
