package com.ndz.payment.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.stripe.Stripe;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.config.StripeProperties;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.DeliveryFailure;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.DeliveryFailurePage;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.PaymentConfig;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.ReprocessResult;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.WebhookCheckResult;
import com.ndz.payment.repository.ConnectedAccountRepository;
import com.ndz.payment.repository.StripeEventRepository;
import com.ndz.payment.service.AdminStripeConfigService;
import com.ndz.payment.service.StripeWebhookService;

@Service
class AdminStripeConfigServiceImpl implements AdminStripeConfigService {

	private static final int FAILED_DELIVERIES_LIMIT = 20;

	private final StripeProperties properties;
	private final StripeGateway stripe;
	private final StripeWebhookService webhooks;
	private final StripeEventRepository stripeEvents;
	private final ConnectedAccountRepository connectedAccounts;
	private final CatalogApi catalog;
	private final Clock clock;
	private final int serverPort;

	AdminStripeConfigServiceImpl(StripeProperties properties, StripeGateway stripe, StripeWebhookService webhooks,
			StripeEventRepository stripeEvents, ConnectedAccountRepository connectedAccounts, CatalogApi catalog,
			Clock clock, @Value("${server.port:8081}") int serverPort) {
		this.properties = properties;
		this.stripe = stripe;
		this.webhooks = webhooks;
		this.stripeEvents = stripeEvents;
		this.connectedAccounts = connectedAccounts;
		this.catalog = catalog;
		this.clock = clock;
		this.serverPort = serverPort;
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentConfig config() {
		Instant since = clock.instant().minusSeconds(24 * 3600L);
		long received = stripeEvents.countByReceivedAtGreaterThanEqual(since);
		long processed = stripeEvents.countByReceivedAtGreaterThanEqualAndProcessedAtIsNotNull(since);
		double successRate = received == 0 ? 100.0 : (processed * 100.0) / received;
		Map<String, Integer> byState = new HashMap<>(connectedAccounts.countByState());
		int withAccount = connectedAccounts.countTotal();
		int none = Math.max(0, (int) catalog.countApprovedOrganizers() - withAccount);
		byState.merge("none", none, Integer::sum);
		return new PaymentConfig(mode(), maskedSecretKey(), Stripe.API_VERSION, "/api/v1/webhooks/stripe",
				"/api/v1/webhooks/stripe-connect", Math.round(successRate * 10) / 10.0, byState);
	}

	@Override
	public DeliveryFailurePage failedDeliveries() {
		return new DeliveryFailurePage(stripe.listFailedDeliveries(FAILED_DELIVERIES_LIMIT).stream()
				.map(failure -> new DeliveryFailure(failure.id(), failure.type(), failure.createdAt(),
						failure.pendingWebhooks()))
				.toList());
	}

	@Override
	public ReprocessResult reprocess(String stripeEventId) {
		return new ReprocessResult(stripeEventId, webhooks.reprocess(stripeEventId));
	}

	@Override
	public WebhookCheckResult webhookCheck() {
		String payload = """
				{"id":"evt_admin_check","object":"event","type":"ping","data":{"object":{}}}""";
		String signature = sign(payload);
		try {
			RestClient.create().post().uri("http://localhost:" + serverPort + "/api/v1/webhooks/stripe")
					.header("Content-Type", "application/json").header("Stripe-Signature", signature)
					.body(payload).retrieve().toBodilessEntity();
			return new WebhookCheckResult(HttpStatus.OK.value(), true, null);
		}
		catch (RestClientResponseException e) {
			return new WebhookCheckResult(e.getStatusCode().value(), false, e.getStatusText());
		}
		catch (RuntimeException e) {
			return new WebhookCheckResult(0, false, e.getMessage());
		}
	}

	private String sign(String payload) {
		long timestamp = clock.instant().getEpochSecond();
		String signedPayload = timestamp + "." + payload;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String signature = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
			return "t=" + timestamp + ",v1=" + signature;
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Không ký được payload thử", e);
		}
	}

	private String mode() {
		String key = properties.secretKey();
		if (key == null || key.isBlank()) {
			return "not_configured";
		}
		return key.startsWith("sk_live_") ? "live" : "test";
	}

	private String maskedSecretKey() {
		String key = properties.secretKey();
		if (key == null || key.length() < 4) {
			return "";
		}
		return "•".repeat(Math.max(0, key.length() - 4)) + key.substring(key.length() - 4);
	}

}
