package com.ndz.payment.client;

/**
 * Webhook của Stripe đã xác thực chữ ký, rút gọn cho domain. {@code paymentIntentId} chỉ có với event của
 * PaymentIntent; {@code failureCode} (vd. {@code card_declined}) chỉ có khi thanh toán thất bại.
 */
public record GatewayWebhookEvent(String id, String type, String paymentIntentId, String failureCode) {

	public static final String PAYMENT_INTENT_PROCESSING = "payment_intent.processing";
	public static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
	public static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";

}
