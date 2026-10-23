package com.ndz.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Rate limit ở Gateway (plan Sprint 7 hạng mục 5; overview.md §13.4, §15.2) với Redis thật: vượt giới hạn → 429 kèm
 * {@code Retry-After}, user đã đăng nhập chỉ bị giới hạn theo tài khoản, Redis chết → fail-open có metric.
 */
@Testcontainers
class RateLimitFilterTests {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.5").withExposedPorts(6379);

	private static RedisClient client;

	private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

	// bookings 10/phút, queue-join 20/phút, user 5/giây, anonymous 3/giây (nhỏ để test nhanh).
	private final RateLimitProperties properties = new RateLimitProperties(true, Duration.ofMillis(200),
			Duration.ofSeconds(5), 10, 20, 5, 3);
	private final RateLimiter limiter = new RateLimiter(client, properties, metrics);
	private final RateLimitFilter filter = new RateLimitFilter(limiter, properties);

	@BeforeAll
	static void connect() {
		client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
	}

	@AfterAll
	static void disconnect() {
		client.shutdown();
	}

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("AC-8: vượt giới hạn POST /bookings → 429 RATE_LIMITED kèm Retry-After")
	void exceedingTheBookingLimitIsRejectedWithRetryAfter() throws Exception {
		signIn(UUID.randomUUID());
		for (int i = 0; i < 10; i++) {
			assertThat(call("POST", "/api/v1/bookings", "10.0.0.1").getStatus()).isEqualTo(200);
		}

		MockHttpServletResponse rejected = call("POST", "/api/v1/bookings", "10.0.0.1");

		assertThat(rejected.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isBetween(1L, 60L);
		assertThat(rejected.getContentType()).startsWith("application/problem+json");
		assertThat(rejected.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"").contains("\"status\":429");
	}

	@Test
	@DisplayName("AC-8: hai user đăng nhập cùng IP không ăn chung giới hạn")
	void twoSignedInUsersOnTheSameIpDoNotShareALimit() throws Exception {
		UUID first = UUID.randomUUID();
		signIn(first);
		for (int i = 0; i < 10; i++) {
			call("POST", "/api/v1/bookings", "10.0.0.2");
		}
		assertThat(call("POST", "/api/v1/bookings", "10.0.0.2").getStatus()).isEqualTo(429);

		signIn(UUID.randomUUID());

		assertThat(call("POST", "/api/v1/bookings", "10.0.0.2").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("AC-8: user đăng nhập không bị giới hạn theo IP, dù IP đó đã hết lượt của khách chưa đăng nhập")
	void signedInUsersAreNotLimitedByIp() throws Exception {
		for (int i = 0; i < 3; i++) {
			call("GET", "/api/v1/events", "10.0.0.3");
		}
		assertThat(call("GET", "/api/v1/events", "10.0.0.3").getStatus()).isEqualTo(429);

		signIn(UUID.randomUUID());

		assertThat(call("GET", "/api/v1/events", "10.0.0.3").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Request chưa đăng nhập bị giới hạn theo IP; IP khác không ảnh hưởng")
	void anonymousRequestsAreLimitedPerIp() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(call("GET", "/api/v1/events", "10.0.0.4").getStatus()).isEqualTo(200);
		}

		assertThat(call("GET", "/api/v1/events", "10.0.0.4").getStatus()).isEqualTo(429);
		assertThat(call("GET", "/api/v1/events", "10.0.0.5").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("POST /queue/{id}/join có giới hạn riêng; webhook Stripe và đường không phải API không bị giới hạn")
	void queueJoinHasItsOwnLimitAndWebhooksAreExempt() throws Exception {
		signIn(UUID.randomUUID());
		String join = "/api/v1/queue/" + UUID.randomUUID() + "/join";
		for (int i = 0; i < 20; i++) {
			assertThat(call("POST", join, "10.0.0.6").getStatus()).isEqualTo(200);
		}
		assertThat(call("POST", join, "10.0.0.6").getStatus()).isEqualTo(429);

		SecurityContextHolder.clearContext();
		for (int i = 0; i < 20; i++) {
			assertThat(call("POST", "/api/v1/webhooks/stripe", "10.0.0.7").getStatus()).isEqualTo(200);
			assertThat(call("GET", "/assets/app.js", "10.0.0.7").getStatus()).isEqualTo(200);
		}
	}

	@Test
	@DisplayName("AC-8: Redis chết → fail-open (request vẫn qua), metric tickethub.gateway.ratelimit.bypassed tăng")
	void whenRedisIsDownRequestsPassAndTheBypassIsCounted() throws Exception {
		assertThat(call("GET", "/api/v1/events", "10.0.0.8").getStatus()).isEqualTo(200);
		assertThat(bypassed()).isZero();

		REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
		try {
			for (int i = 0; i < 10; i++) {
				assertThat(call("GET", "/api/v1/events", "10.0.0.8").getStatus()).isEqualTo(200);
			}
		}
		finally {
			REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
		}

		assertThat(bypassed()).isEqualTo(10);
	}

	// ---- Hỗ trợ ----

	private double bypassed() {
		return metrics.get("tickethub.gateway.ratelimit.bypassed").counter().count();
	}

	private static void signIn(UUID userId) {
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(userId.toString(), "n/a", "ROLE_USER"));
	}

	private MockHttpServletResponse call(String method, String path, String ip) throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(ip);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

}
