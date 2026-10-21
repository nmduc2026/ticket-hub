package com.ndz.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có mã ổn định cho frontend (vd. {@code SEAT_UNAVAILABLE}); được chuyển thành Problem Details.
 * Trạng thái HTTP lấy từ {@link ErrorCode}.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;
	private final Map<String, Object> properties = new LinkedHashMap<>();

	public BusinessException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	/** Thêm thông tin có cấu trúc vào Problem Details (vd. danh sách mục còn thiếu), để frontend hiển thị chi tiết. */
	public BusinessException with(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public Map<String, Object> properties() {
		return Map.copyOf(properties);
	}

	public ErrorCode code() {
		return code;
	}

	public HttpStatus status() {
		return code.status();
	}

}
