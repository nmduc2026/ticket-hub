package com.ndz.payment.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import com.ndz.payment.config.StripeConfiguration;
import com.ndz.payment.config.StripeProperties;

/**
 * Resilience4j quanh Stripe (plan Sprint 4 việc 6; AC-9; overview.md §15.1): timeout 3 giây, retry chỉ khi có
 * idempotency key, circuit breaker, bulkhead. Stripe giả lập bằng WireMock, không cần Docker.
 */
class StripeResilienceTests {

	private static final String INTENT_JSON = """
			{"id":"pi_123","object":"payment_intent","amount":50000,"currency":"vnd",
			 "status":"requires_payment_method","client_secret":"placeholder-client-secret"}
			""";
	private static final String REFUND_JSON = """
			{"id":"re_1","object":"refund","amount":50000,"currency":"vnd","status":"succeeded"}
			""";
	private static final String SERVER_ERROR = """
			{"error":{"type":"api_error","message":"boom"}}
			""";
	private static final String CARD_ERROR = """
			{"error":{"type":"invalid_request_error","code":"charge_already_refunded","message":"already refunded"}}
			""";

	private static WireMockServer stripe;

	private StripeResilience resilience;
	private StripeGateway gateway;

	@BeforeAll
	static void startStripe() {
		stripe = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		stripe.start();
	}

	@AfterAll
	static void stopStripe() {
		stripe.stop();
	}

	@BeforeEach
	void setUp() {
		stripe.resetAll();
		build(StripeProperties.Resilience.defaults());
	}

	@Test
	@DisplayName("AC-9: Stripe trễ 5 giây → lời gọi dừng ở 3 giây")
	void aSlowStripeCallStopsAtThreeSeconds() {
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(
				aResponse().withFixedDelay(5000).withHeader("Content-Type", "application/json").withBody(INTENT_JSON)));

		long started = System.nanoTime();
		assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class);
		Duration took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(2900)).isLessThan(Duration.ofMillis(4500));
	}

	@Test
	@DisplayName("AC-9: lời gọi có idempotency key được thử lại khi lỗi tạm thời: 500, 500, rồi thành công")
	void aCallWithAnIdempotencyKeyIsRetriedAfterTransientErrors() {
		UUID booking = UUID.randomUUID();
		stubSequence("/v1/payment_intents", List.of(serverError(), serverError(), ok(INTENT_JSON)));

		var intent = gateway.createPaymentIntent(50_000, "VND", booking, "booking-" + booking);

		assertThat(intent.id()).isEqualTo("pi_123");
		stripe.verify(3, postRequestedFor(urlEqualTo("/v1/payment_intents")));
	}

	@Test
	@DisplayName("AC-9: lỗi tạm thời kéo dài → thử đúng 3 lần rồi báo lỗi")
	void retryGivesUpAfterThreeAttempts() {
		UUID refundId = UUID.randomUUID();
		stripe.stubFor(post(urlEqualTo("/v1/refunds")).willReturn(serverError()));

		assertThatThrownBy(() -> gateway.createRefund("pi_123", 50_000, refundId, "refund-" + refundId))
				.isInstanceOf(StripeGatewayException.class);

		stripe.verify(3, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	@Test
	@DisplayName("AC-9: lời gọi không có idempotency key (retrieve, cancel) không được retry")
	void callsWithoutAnIdempotencyKeyAreNeverRetried() {
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(serverError()));
		stripe.stubFor(post(urlEqualTo("/v1/payment_intents/pi_123/cancel")).willReturn(serverError()));

		assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class);
		assertThatThrownBy(() -> gateway.cancelPaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class);

		stripe.verify(1, getRequestedFor(urlEqualTo("/v1/payment_intents/pi_123")));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents/pi_123/cancel")));
	}

	@Test
	@DisplayName("Lỗi nghiệp vụ của Stripe (4xx có mã) không được retry và không làm mở breaker")
	void businessErrorsAreNotRetriedAndDoNotCountAgainstTheBreaker() {
		UUID refundId = UUID.randomUUID();
		stripe.stubFor(post(urlEqualTo("/v1/refunds")).willReturn(aResponse().withStatus(400)
				.withHeader("Content-Type", "application/json").withBody(CARD_ERROR)));

		for (int i = 0; i < 25; i++) {
			assertThatThrownBy(() -> gateway.createRefund("pi_123", 50_000, refundId, "refund-" + refundId))
					.isInstanceOf(StripeGatewayException.class)
					.extracting(e -> ((StripeGatewayException) e).code()).isEqualTo("charge_already_refunded");
		}

		stripe.verify(25, postRequestedFor(urlEqualTo("/v1/refunds")));
		assertThat(resilience.state()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	@DisplayName("AC-9: lỗi liên tiếp đạt ngưỡng → breaker mở, các lời gọi sau báo lỗi ngay, không chạm Stripe")
	void consecutiveFailuresOpenTheCircuitBreaker() {
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(serverError()));

		for (int i = 0; i < 20; i++) {
			assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class);
		}
		assertThat(resilience.state()).isEqualTo(CircuitBreaker.State.OPEN);

		long started = System.nanoTime();
		assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class)
				.hasMessageContaining("circuit breaker").extracting(e -> ((StripeGatewayException) e).code()).isNull();
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(200));
		// Cả lời gọi có idempotency key cũng bị chặn khi breaker mở, và không bị retry vô ích.
		assertThatThrownBy(() -> gateway.createRefund("pi_123", 50_000, UUID.randomUUID(), "refund-x"))
				.isInstanceOf(StripeGatewayException.class);

		stripe.verify(20, getRequestedFor(urlEqualTo("/v1/payment_intents/pi_123")));
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	@Test
	@DisplayName("Breaker sang half-open sau thời gian mở và đóng lại khi Stripe khoẻ")
	void theBreakerRecoversThroughHalfOpen() throws InterruptedException {
		build(new StripeProperties.Resilience(3, Duration.ofMillis(200), 2, 0.5, 50, 20, Duration.ofMillis(300), 5, 20));
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(serverError()));
		for (int i = 0; i < 20; i++) {
			assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123"));
		}
		assertThat(resilience.state()).isEqualTo(CircuitBreaker.State.OPEN);

		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(ok(INTENT_JSON)));
		Thread.sleep(400);
		for (int i = 0; i < 5; i++) {
			assertThat(gateway.retrievePaymentIntent("pi_123").id()).isEqualTo("pi_123");
		}

		assertThat(resilience.state()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	@DisplayName("Bulkhead: vượt số lời gọi đồng thời tối đa thì bị từ chối ngay")
	void callsBeyondTheBulkheadLimitAreRejectedImmediately() throws Exception {
		build(new StripeProperties.Resilience(3, Duration.ofMillis(200), 2, 0.5, 50, 20, Duration.ofSeconds(30), 5, 2));
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(
				aResponse().withFixedDelay(800).withHeader("Content-Type", "application/json").withBody(INTENT_JSON)));
		ExecutorService pool = Executors.newFixedThreadPool(3);
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				futures.add(pool.submit(() -> {
					go.await();
					try {
						return gateway.retrievePaymentIntent("pi_123").id();
					}
					catch (StripeGatewayException e) {
						return "rejected: " + e.getMessage();
					}
				}));
			}
			go.countDown();
			List<String> results = new ArrayList<>();
			for (Future<String> future : futures) {
				results.add(future.get(10, TimeUnit.SECONDS));
			}
			assertThat(results).filteredOn(result -> result.equals("pi_123")).hasSize(2);
			assertThat(results).filteredOn(result -> result.startsWith("rejected: Stripe quá tải")).hasSize(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ---- helpers ----

	private void build(StripeProperties.Resilience config) {
		var properties = new StripeProperties("dummy-key", "dummy-webhook-secret", null, "http://r", "http://f", stripe.baseUrl(),
				Duration.ofSeconds(3), Duration.ofMinutes(5), config);
		var configuration = new StripeConfiguration();
		resilience = configuration.stripeResilience(properties, new SimpleMeterRegistry());
		gateway = configuration.stripeGateway(configuration.stripeClient(properties), resilience, properties,
				Clock.systemUTC());
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder serverError() {
		return aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody(SERVER_ERROR);
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder ok(String body) {
		return aResponse().withHeader("Content-Type", "application/json").withBody(body);
	}

	/** Mỗi lần gọi nhận một phản hồi theo thứ tự; lần cuối lặp lại. */
	private static void stubSequence(String url, List<com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder> responses) {
		String state = Scenario.STARTED;
		for (int i = 0; i < responses.size(); i++) {
			String next = i == responses.size() - 1 ? state : "step-" + (i + 1);
			stripe.stubFor(post(urlEqualTo(url)).inScenario("sequence").whenScenarioStateIs(state)
					.willReturn(responses.get(i)).willSetStateTo(next));
			state = next;
		}
	}

}
