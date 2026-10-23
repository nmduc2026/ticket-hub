package com.ndz.gateway.shedding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đếm request đang xử lý và từ chối khi vượt ngưỡng (overview.md §15.3, ADR 0028). Đứng đầu chuỗi filter để từ chối
 * rẻ nhất có thể.
 * <ul>
 * <li>Kết nối SSE đếm riêng, không tính vào ngưỡng: mỗi khách xem sơ đồ giữ một kết nối hàng chục phút.</li>
 * <li>Không bao giờ từ chối webhook Stripe, health probe và {@code POST /payments}: từ chối là mất tiền đã thu hoặc
 * làm pod bị loại khỏi vòng phục vụ.</li>
 * <li>API đọc ({@code GET}) có ngưỡng thấp hơn nên bị từ chối trước.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LoadSheddingFilter extends OncePerRequestFilter {

	private static final String API = "/api/";
	private static final String WEBHOOKS = "/api/v1/webhooks/";
	private static final String PAYMENTS = "/api/v1/payments";
	private static final String ACTUATOR = "/actuator/";
	private static final String SSE_SUFFIX = "/stream";

	private final LoadSheddingProperties properties;
	private final AtomicInteger inFlight = new AtomicInteger();
	private final AtomicInteger streams = new AtomicInteger();
	private final Counter shedReads;
	private final Counter shedWrites;

	public LoadSheddingFilter(LoadSheddingProperties properties, MeterRegistry metrics) {
		this.properties = properties;
		Gauge.builder("tickethub.gateway.inflight", inFlight, AtomicInteger::get)
				.description("Request đang xử lý (không tính SSE)").register(metrics);
		Gauge.builder("tickethub.gateway.sse.connections", streams, AtomicInteger::get)
				.description("Kết nối SSE đang mở qua Gateway").register(metrics);
		this.shedReads = Counter.builder("tickethub.gateway.shed").tag("kind", "read")
				.description("Request bị từ chối vì quá tải (503 OVERLOADED)").register(metrics);
		this.shedWrites = Counter.builder("tickethub.gateway.shed").tag("kind", "write")
				.description("Request bị từ chối vì quá tải (503 OVERLOADED)").register(metrics);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isStream(request)) {
			streams.incrementAndGet();
			try {
				chain.doFilter(request, response);
			}
			finally {
				streams.decrementAndGet();
			}
			return;
		}
		boolean read = "GET".equals(request.getMethod());
		if (!isProtected(request) && inFlight.get() >= (read ? properties.maxInFlightReads() : properties.maxInFlight())) {
			(read ? shedReads : shedWrites).increment();
			reject(response);
			return;
		}
		inFlight.incrementAndGet();
		try {
			chain.doFilter(request, response);
		}
		finally {
			inFlight.decrementAndGet();
		}
	}

	private static boolean isStream(HttpServletRequest request) {
		return "GET".equals(request.getMethod()) && request.getRequestURI().endsWith(SSE_SUFFIX);
	}

	/** Không bao giờ bị từ chối vì quá tải: tiền, probe, và mọi thứ không phải API (trang SPA do web server phục vụ). */
	private static boolean isProtected(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith(API) || path.startsWith(WEBHOOKS) || path.startsWith(ACTUATOR)
				|| path.equals(PAYMENTS) || path.startsWith(PAYMENTS + "/");
	}

	private void reject(HttpServletResponse response) throws IOException {
		response.setStatus(503);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, properties.retryAfter().toSeconds())));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(
				"{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"Hệ thống đang quá tải, vui lòng thử lại sau ít giây\",\"code\":\"OVERLOADED\"}");
	}

}
