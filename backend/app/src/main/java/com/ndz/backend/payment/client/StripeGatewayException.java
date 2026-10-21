package com.ndz.backend.payment.client;

/**
 * Lời gọi Stripe thất bại. {@code code} là mã lỗi của Stripe (vd. {@code payment_intent_unexpected_state}) khi có;
 * lỗi mạng hoặc quá thời gian thì {@code code} là null.
 */
public class StripeGatewayException extends RuntimeException {

	private final String code;

	public StripeGatewayException(String message, String code, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}

}
