package com.ndz.backend.shared.exception;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Mọi lỗi trả về theo RFC 9457 (Problem Details) kèm {@code code} nghiệp vụ và {@code traceId}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
		problem.setProperty("code", ex.code().name());
		ex.properties().forEach(problem::setProperty);
		addTraceId(problem);
		return problem;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) throws Exception {
		// 401 / 403 do Spring Security xử lý (ExceptionTranslationFilter), không phải lỗi hệ thống.
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		log.error("Lỗi không mong đợi", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Đã xảy ra lỗi hệ thống");
		problem.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
		addTraceId(problem);
		return problem;
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Dữ liệu không hợp lệ");
		problem.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(),
						"message", String.valueOf(error.getDefaultMessage())))
				.toList();
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** Lỗi của Spring MVC (404, 405, 415...) chưa có mã nghiệp vụ thì lấy tên trạng thái HTTP làm {@code code}. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
				HttpStatus status = HttpStatus.resolve(statusCode.value());
				problem.setProperty("code", status != null ? status.name() : "ERROR");
			}
			addTraceId(problem);
		}
		return response;
	}

	private static void addTraceId(ProblemDetail problem) {
		String traceId = MDC.get("traceId");
		if (traceId != null) {
			problem.setProperty("traceId", traceId);
		}
	}

}
