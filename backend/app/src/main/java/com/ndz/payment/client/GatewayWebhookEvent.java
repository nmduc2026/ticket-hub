package com.ndz.payment.client;

/**
 * Webhook của Stripe đã xác thực chữ ký, rút gọn cho domain. {@code paymentIntentId} có với event của PaymentIntent,
 * Charge và Refund; {@code failureCode} (vd. {@code card_declined}, {@code expired_or_canceled_card}) chỉ có khi thanh
 * toán hoặc hoàn tiền thất bại; {@code stripeRefundId} chỉ có với event của Refund.
 */
public record GatewayWebhookEvent(String id, String type, String paymentIntentId, String failureCode,
		String stripeRefundId) {

	public static final String PAYMENT_INTENT_PROCESSING = "payment_intent.processing";
	public static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
	public static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
	public static final String CHARGE_REFUNDED = "charge.refunded";
	public static final String REFUND_FAILED = "refund.failed";

}
