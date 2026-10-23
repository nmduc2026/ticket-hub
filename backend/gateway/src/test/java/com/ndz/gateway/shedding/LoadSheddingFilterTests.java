package com.ndz.gateway.shedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Load shedding ở Gateway (plan Sprint 7 hạng mục 7, AC-10; overview.md §15.3, ADR 0028): vượt ngưỡng → 503 OVERLOADED
 * kèm Retry-After, API đọc bị từ chối trước, webhook / health / thanh toán không bao giờ bị từ chối, SSE không tính vào
 * ngưỡng.
 */
class LoadSheddingFilterTests {

	// Ghi 3 request cùng lúc, đọc 2 request cùng lúc.
	private final LoadSheddingProperties properties = new LoadSheddingProperties(true, 3, 2, Duration.ofSeconds(2));
	private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
	private final LoadSheddingFilter filter = new LoadSheddingFilter(properties, metrics);
	private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
	private final CountDownLatch release = new CountDownLatch(1);

	@AfterEach
	void releaseEveryone() {
		release.countDown();
		pool.close();
	}

	@Test
	@DisplayName("AC-10: vượt ngưỡng → 503 OVERLOADED kèm Retry-After, request không xuống tới backend")
	void aboveTheThresholdRequestsAreRejectedWithRetryAfter() throws Exception {
		occupy(3, "POST", "/api/v1/bookings");

		MockHttpServletResponse rejected = new MockHttpServletResponse();
		boolean[] reachedBackend = { false };
		filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/bookings"), rejected,
				(req, res) -> reachedBackend[0] = true);

		assertThat(rejected.getStatus()).isEqualTo(503);
		assertThat(rejected.getHeader("Retry-After")).isEqualTo("2");
		assertThat(rejected.getContentAsString()).contains("\"code\":\"OVERLOADED\"");
		assertThat(reachedBackend[0]).isFalse();
		assertThat(metrics.get("tickethub.gateway.shed").tag("kind", "write").counter().count()).isEqualTo(1);
	}

	@Test
	@DisplayName("API đọc bị từ chối trước: ngưỡng đọc thấp hơn, còn chỗ cho request ghi")
	void readsAreShedBeforeWrites() throws Exception {
		occupy(2, "POST", "/api/v1/bookings");

		assertThat(call("GET", "/api/v1/events").getStatus()).isEqualTo(503);
		assertThat(call("POST", "/api/v1/bookings").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("AC-10: lúc đang từ chối, webhook Stripe, health và POST /payments vẫn qua")
	void protectedPathsAreNeverShed() throws Exception {
		occupy(3, "POST", "/api/v1/bookings");

		assertThat(call("POST", "/api/v1/webhooks/stripe").getStatus()).isEqualTo(200);
		assertThat(call("GET", "/actuator/health/readiness").getStatus()).isEqualTo(200);
		assertThat(call("POST", "/api/v1/payments").getStatus()).isEqualTo(200);
		assertThat(call("GET", "/events/42").getStatus()).isEqualTo(200);
		assertThat(call("POST", "/api/v1/bookings").getStatus()).isEqualTo(503);
	}

	@Test
	@DisplayName("AC-10: 500 kết nối SSE mở không kích hoạt shed, được đếm riêng")
	void streamsDoNotCountTowardsTheThreshold() throws Exception {
		occupy(500, "GET", "/api/v1/events/42/seats/stream");

		assertThat(call("POST", "/api/v1/bookings").getStatus()).isEqualTo(200);
		assertThat(call("GET", "/api/v1/events").getStatus()).isEqualTo(200);
		assertThat(metrics.get("tickethub.gateway.sse.connections").gauge().value()).isEqualTo(500);
		assertThat(metrics.get("tickethub.gateway.inflight").gauge().value()).isZero();
	}

	@Test
	@DisplayName("Request xong thì nhả chỗ: hết tải thì nhận request lại bình thường")
	void capacityIsReleasedWhenRequestsFinish() throws Exception {
		occupy(3, "POST", "/api/v1/bookings");
		assertThat(call("POST", "/api/v1/bookings").getStatus()).isEqualTo(503);

		release.countDown();

		await().atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> assertThat(call("POST", "/api/v1/bookings").getStatus()).isEqualTo(200));
	}

	// ---- Hỗ trợ ----

	/** Giữ {@code count} request đang xử lý (chặn trong backend giả) cho tới khi test kết thúc. */
	private void occupy(int count, String method, String path) throws Exception {
		FilterChain blocking = (req, res) -> {
			try {
				release.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		CountDownLatch entered = new CountDownLatch(count);
		for (int i = 0; i < count; i++) {
			pool.submit(() -> {
				entered.countDown();
				filter.doFilter(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), blocking);
				return null;
			});
		}
		entered.await();
		// Đã vào filter nhưng có thể chưa kịp tăng bộ đếm: chờ tới khi bộ đếm thấy đủ.
		String meter = path.endsWith("/stream") ? "tickethub.gateway.sse.connections" : "tickethub.gateway.inflight";
		await().atMost(Duration.ofSeconds(5)).untilAsserted(
				() -> assertThat(metrics.get(meter).gauge().value()).isEqualTo(count));
	}

	private MockHttpServletResponse call(String method, String path) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(new MockHttpServletRequest(method, path), response, (req, res) -> {
		});
		return response;
	}

}
