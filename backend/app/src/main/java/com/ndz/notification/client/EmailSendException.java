package com.ndz.notification.client;

/** Gửi email thất bại; thông báo không chứa địa chỉ người nhận. */
public class EmailSendException extends RuntimeException {

	public EmailSendException(String message, Throwable cause) {
		super(message, cause);
	}

}
