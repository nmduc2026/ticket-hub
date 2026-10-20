package com.ndz.backend.shared;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có mã ổn định cho frontend (vd. {@code SEAT_UNAVAILABLE}); được chuyển thành Problem Details.
 */
public class BusinessException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public BusinessException(String code, String message) {
		this(code, message, HttpStatus.UNPROCESSABLE_CONTENT);
	}

	public BusinessException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

}
