package com.ndz.backend.payment.client;

import com.ndz.backend.payment.config.StripeConfiguration;
import com.ndz.backend.payment.config.StripeProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Adapter Stripe trên WireMock giả lập API (không cần Docker). Kiểm hình dạng lời gọi và cách ánh xạ lỗi; việc Stripe
 * khử trùng theo idempotency key chỉ kiểm được trên stripe-mock (xem docs/plans/2026-10-19-sprint-3-pending.md).
 */
class StripeGatewayTests {

	private static final String INTENT_JSON = """
			{"id":"pi_123","object":"payment_intent","amount":50000,"currency":"vnd",
			 "status":"%s","client_secret":"placeholder-client-secret"}
			""";

	private static WireMockServer stripe;

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
		gateway = gatewayWithTimeout(Duration.ofSeconds(3));
	}

	@Test
	@DisplayName("AC-8: tạo PaymentIntent gửi số tiền VND nguyên, bookingId trong metadata và idempotency key")
	void createsPaymentIntentWithIdempotencyKey() {
		UUID bookingId = UUID.randomUUID();
		stripe.stubFor(post(urlEqualTo("/v1/payment_intents")).willReturn(
				aResponse().withHeader("Content-Type", "application/json").withBody(INTENT_JSON.formatted("requires_payment_method"))));

		var intent = gateway.createPaymentIntent(50_000, "VND", bookingId, "booking-" + bookingId);

		assertThat(intent).isEqualTo(
				new GatewayPaymentIntent("pi_123", "requires_payment_method", "placeholder-client-secret", 50_000, "VND"));
		stripe.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
				.withHeader("Idempotency-Key", equalTo("booking-" + bookingId))
				.withRequestBody(containing("amount=50000")).withRequestBody(containing("currency=vnd"))
				.withRequestBody(containing("metadata[bookingId]=" + bookingId)));
	}

	@Test
	void retrievesPaymentIntent() {
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(
				aResponse().withHeader("Content-Type", "application/json").withBody(INTENT_JSON.formatted("succeeded"))));

		var intent = gateway.retrievePaymentIntent("pi_123");

		assertThat(intent.isSucceeded()).isTrue();
	}

	@Test
	void cancelsPaymentIntent() {
		stripe.stubFor(post(urlEqualTo("/v1/payment_intents/pi_123/cancel")).willReturn(
				aResponse().withHeader("Content-Type", "application/json").withBody(INTENT_JSON.formatted("canceled"))));

		var intent = gateway.cancelPaymentIntent("pi_123");

		assertThat(intent.status()).isEqualTo("canceled");
		assertThat(intent.isSucceeded()).isFalse();
	}

	@Test
	@DisplayName("Huỷ PaymentIntent đã succeeded: Stripe từ chối, adapter báo mã lỗi để người gọi lấy lại trạng thái")
	void cancelOfSucceededIntentSurfacesStripeErrorCode() {
		stripe.stubFor(post(urlEqualTo("/v1/payment_intents/pi_123/cancel")).willReturn(aResponse().withStatus(400)
				.withHeader("Content-Type", "application/json").withBody("""
						{"error":{"type":"invalid_request_error","code":"payment_intent_unexpected_state",
						 "message":"You cannot cancel this PaymentIntent because it has a status of succeeded."}}
						""")));

		assertThatThrownBy(() -> gateway.cancelPaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class)
				.extracting(e -> ((StripeGatewayException) e).code()).isEqualTo("payment_intent_unexpected_state");
	}

	@Test
	@DisplayName("Quá thời gian chờ: báo lỗi không kèm mã Stripe, không treo")
	void timesOutWhenStripeIsSlow() {
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_123")).willReturn(
				aResponse().withFixedDelay(1500).withHeader("Content-Type", "application/json")
						.withBody(INTENT_JSON.formatted("succeeded"))));
		var impatient = gatewayWithTimeout(Duration.ofMillis(300));

		assertThatThrownBy(() -> impatient.retrievePaymentIntent("pi_123")).isInstanceOf(StripeGatewayException.class)
				.extracting(e -> ((StripeGatewayException) e).code()).isNull();
	}

	private static StripeGateway gatewayWithTimeout(Duration timeout) {
		var properties = new StripeProperties("dummy-key", "dummy-webhook-secret", stripe.baseUrl(), timeout,
				Duration.ofMinutes(5));
		var configuration = new StripeConfiguration();
		return configuration.stripeGateway(configuration.stripeClient(properties));
	}

}
