package com.ndz.backend.shared;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có mã ổn định cho frontend (vd. {@code SEAT_UNAVAILABLE}); được chuyển thành Problem Details.
 */
public class BusinessException extends RuntimeException {

	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> properties = new LinkedHashMap<>();

	public BusinessException(String code, String message) {
		this(code, message, HttpStatus.UNPROCESSABLE_CONTENT);
	}

	public BusinessException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	/** Thêm thông tin có cấu trúc vào Problem Details (vd. danh sách mục còn thiếu), để frontend hiển thị chi tiết. */
	public BusinessException with(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public Map<String, Object> properties() {
		return Map.copyOf(properties);
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

}
