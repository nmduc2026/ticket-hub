package com.ndz.backend.payment.internal.stripe;

/**
 * Bản chụp PaymentIntent mà domain cần, tách khỏi kiểu của SDK. {@code status} giữ nguyên chuỗi của Stripe
 * ({@code requires_payment_method}, {@code processing}, {@code succeeded}, {@code canceled}...).
 */
public record GatewayPaymentIntent(String id, String status, String clientSecret, long amountMinor, String currency) {

	public static final String SUCCEEDED = "succeeded";

	public boolean isSucceeded() {
		return SUCCEEDED.equals(status);
	}

}
