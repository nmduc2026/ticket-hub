package com.ndz.shared.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.ServletRequestPathUtils;

import com.ndz.shared.util.CurrentUser;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.ProblemWriter;

/**
 * Xử lý {@code Idempotency-Key} cho endpoint có {@link Idempotent} (overview.md §6.7, ADR 0025). Chạy sau bước xác
 * thực (cần người dùng) và trước mọi xử lý nghiệp vụ:
 * <ul>
 * <li>key mới → chạy controller, lưu response (trừ 5xx, vì chưa phải kết quả cuối) để lần gửi lại nhận đúng nó;</li>
 * <li>cùng key, khác request → {@code 422 IDEMPOTENCY_KEY_REUSED};</li>
 * <li>cùng key đang xử lý → {@code 409 REQUEST_IN_PROGRESS};</li>
 * <li>cùng key đã xong → phát lại response cũ.</li>
 * </ul>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

	static final String HEADER = "Idempotency-Key";
	static final String REPLAYED_HEADER = "Idempotent-Replayed";
	private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,128}");
	private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

	private final RequestMappingHandlerMapping handlerMapping;
	private final IdempotencyStore store;
	private final IdempotencyProperties properties;
	private final Clock clock;

	IdempotencyFilter(RequestMappingHandlerMapping handlerMapping, IdempotencyStore store,
			IdempotencyProperties properties, Clock clock) {
		this.handlerMapping = handlerMapping;
		this.store = store;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<Idempotent> annotation = idempotentAnnotation(request);
		if (annotation.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		String scope = annotation.get().scope();

		String key = request.getHeader(HEADER);
		if (key == null || key.isBlank()) {
			ProblemWriter.write(response, ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"Thiếu header Idempotency-Key");
			return;
		}
		if (!VALID_KEY.matcher(key).matches()) {
			ProblemWriter.write(response, ErrorCode.IDEMPOTENCY_KEY_INVALID,
					"Idempotency-Key gồm 1-128 ký tự in được, không có khoảng trắng");
			return;
		}
		byte[] body = request.getInputStream().readNBytes(properties.maxBodySize() + 1);
		if (body.length > properties.maxBodySize()) {
			ProblemWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE,
					"Nội dung yêu cầu quá lớn");
			return;
		}

		UUID userId = CurrentUser.require().id();
		String requestHash = hash(request, body);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		boolean claimed = store.claim(scope, userId, key, requestHash, now, now.plus(properties.retention()),
				now.minus(properties.inProgressTimeout()));
		if (claimed) {
			execute(new CachedBodyRequest(request, body), response, chain, scope, userId, key, now);
			return;
		}
		respondToExisting(response, scope, userId, key, requestHash);
	}

	private void execute(CachedBodyRequest request, HttpServletResponse response, FilterChain chain, String scope,
			UUID userId, String key, Instant claimedAt) throws IOException, ServletException {
		ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
		try {
			chain.doFilter(request, wrapper);
		}
		catch (IOException | ServletException | RuntimeException e) {
			store.release(scope, userId, key, claimedAt);
			throw e;
		}
		if (wrapper.getStatus() >= 500) {
			store.release(scope, userId, key, claimedAt);
		}
		else {
			store.complete(scope, userId, key, claimedAt, new StoredResponse(wrapper.getStatus(),
					new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8), wrapper.getContentType()));
		}
		wrapper.copyBodyToResponse();
	}

	private void respondToExisting(HttpServletResponse response, String scope, UUID userId, String key,
			String requestHash) throws IOException {
		Optional<IdempotencyRecord> existing = store.find(scope, userId, key);
		if (existing.isEmpty()) {
			// Bản ghi vừa bị dọn hoặc bị chủ cũ nhả giữa hai câu lệnh; để client gửi lại.
			ProblemWriter.write(response, ErrorCode.REQUEST_IN_PROGRESS,
					"Yêu cầu đang được xử lý, vui lòng thử lại sau");
			return;
		}
		IdempotencyRecord record = existing.get();
		if (!record.requestHash().equals(requestHash)) {
			ProblemWriter.write(response, ErrorCode.IDEMPOTENCY_KEY_REUSED,
					"Idempotency-Key này đã được dùng cho một yêu cầu khác");
		}
		else if (!record.completed()) {
			ProblemWriter.write(response, ErrorCode.REQUEST_IN_PROGRESS,
					"Yêu cầu đang được xử lý, vui lòng thử lại sau");
		}
		else {
			StoredResponse stored = record.response();
			log.info("Phát lại response đã lưu cho Idempotency-Key, scope={}", scope);
			response.setStatus(stored.status());
			if (stored.contentType() != null) {
				response.setContentType(stored.contentType());
			}
			response.setHeader(REPLAYED_HEADER, "true");
			response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
		}
	}

	private Optional<Idempotent> idempotentAnnotation(HttpServletRequest request) throws ServletException {
		boolean parsedHere = !ServletRequestPathUtils.hasParsedRequestPath(request);
		if (parsedHere) {
			ServletRequestPathUtils.parseAndCache(request);
		}
		try {
			HandlerExecutionChain handler = handlerMapping.getHandler(request);
			if (handler != null && handler.getHandler() instanceof HandlerMethod method) {
				return Optional.ofNullable(method.getMethodAnnotation(Idempotent.class));
			}
			return Optional.empty();
		}
		catch (Exception e) {
			throw new ServletException("Không xác định được handler của request", e);
		}
		finally {
			// Trả request về trạng thái ban đầu để DispatcherServlet tự phân tích đường dẫn như thường.
			request.removeAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
			request.removeAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
			request.removeAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
			request.removeAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
			if (parsedHere) {
				ServletRequestPathUtils.clearParsedRequestPath(request);
			}
		}
	}

	/** Hash của phương thức, đường dẫn, query và body: cùng key mà khác một trong bốn thứ là request khác. */
	private static String hash(HttpServletRequest request, byte[] body) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String target = request.getMethod() + " " + request.getRequestURI() + "?"
					+ (request.getQueryString() == null ? "" : request.getQueryString()) + "\n";
			digest.update(target.getBytes(StandardCharsets.UTF_8));
			digest.update(body);
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
