package com.ndz.gateway.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ndz.gateway.ratelimit.RateLimiter.Decision;

/**
 * Giới hạn tốc độ request vào {@code /api/**} (overview.md §13.4). Đặt trong chuỗi bảo mật, sau khi session đã nạp
 * để biết user là ai nhưng trước khi từ chối request chưa đăng nhập, nên spam request chưa đăng nhập cũng bị chặn.
 * <ul>
 * <li>User đã đăng nhập: theo {@code rl:{userId}:{nhóm}}, không phụ thuộc IP.</li>
 * <li>Chưa đăng nhập: theo {@code rl:ip:{ip}:anonymous}. IP lấy từ {@code request.getRemoteAddr()}, đã được
 * {@code ForwardedHeaderFilter} thay bằng {@code X-Forwarded-For} của proxy phía trước (Gateway chỉ nhận request qua
 * proxy tin cậy, proxy này ghi đè header).</li>
 * </ul>
 * Webhook Stripe không bị giới hạn (xác thực bằng chữ ký, Stripe gửi dồn khi thử lại).
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final String API = "/api/";
	private static final String WEBHOOKS = "/api/v1/webhooks/";
	private static final String BOOKINGS = "/api/v1/bookings";
	private static final Pattern QUEUE_JOIN = Pattern.compile("^/api/v1/queue/[^/]+/join$");
	private static final Duration PER_SECOND = Duration.ofSeconds(1);
	private static final Duration PER_MINUTE = Duration.ofMinutes(1);

	private final RateLimiter limiter;
	private final RateLimitProperties properties;

	public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties) {
		this.limiter = limiter;
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !properties.enabled() || !path.startsWith(API) || path.startsWith(WEBHOOKS);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String userId = userId();
		Decision decision = userId == null ? anonymous(request) : forUser(userId, request);
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}
		reject(response, decision.retryAfterSeconds());
	}

	private Decision anonymous(HttpServletRequest request) {
		return limiter.tryConsume("rl:ip:" + request.getRemoteAddr() + ":anonymous", properties.anonymousPerSecond(),
				PER_SECOND);
	}

	private Decision forUser(String userId, HttpServletRequest request) {
		String path = request.getRequestURI();
		boolean post = "POST".equals(request.getMethod());
		if (post && path.equals(BOOKINGS)) {
			return limiter.tryConsume("rl:" + userId + ":bookings", properties.bookingsPerMinute(), PER_MINUTE);
		}
		if (post && QUEUE_JOIN.matcher(path).matches()) {
			return limiter.tryConsume("rl:" + userId + ":queue-join", properties.queueJoinPerMinute(), PER_MINUTE);
		}
		return limiter.tryConsume("rl:" + userId + ":general", properties.userPerSecond(), PER_SECOND);
	}

	/** Định danh user của session (claim {@code sub} của OIDC); null nếu chưa đăng nhập. */
	private static String userId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			return null;
		}
		return authentication.getName();
	}

	private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
		String traceId = MDC.get("traceId");
		response.setStatus(429);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(
				"{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Bạn thao tác quá nhanh, vui lòng thử lại sau\",\"code\":\"RATE_LIMITED\""
						+ (traceId == null ? "" : ",\"traceId\":\"" + traceId + "\"") + "}");
	}

}
