package com.ndz.payment.client;

/** Webhook không qua được xác thực chữ ký hoặc không đọc được; người gọi trả 400 và không đổi trạng thái gì. */
public class InvalidWebhookException extends RuntimeException {

	public InvalidWebhookException(String message, Throwable cause) {
		super(message, cause);
	}

}
