package com.ndz.backend.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Ghi Problem Details (RFC 9457) từ filter, nơi {@code GlobalExceptionHandler} không với tới vì lỗi xảy ra trước
 * controller (401, 403, lỗi Idempotency-Key). Cùng định dạng với handler: {@code code} nghiệp vụ và {@code traceId}.
 */
public final class ProblemWriter {

	private ProblemWriter() {
	}

	public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
			throws IOException {
		String traceId = MDC.get("traceId");
		String json = "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"%s}"
				.formatted(status.getReasonPhrase(), status.value(), escape(detail), code,
						traceId == null ? "" : ",\"traceId\":\"" + escape(traceId) + "\"");
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
