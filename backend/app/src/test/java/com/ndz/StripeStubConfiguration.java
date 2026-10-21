package com.ndz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Giả lập API Stripe bằng WireMock cho test tích hợp (biên ngoài, không phải thứ ta sở hữu). Mọi test dùng chung một
 * context nên chung một server; mỗi test tự {@code resetAll()} và dựng stub của mình.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StripeStubConfiguration {

	/** Secret ký webhook mà test dùng để tự ký payload. */
	public static final String WEBHOOK_SECRET = "dummy-webhook-secret";

	@Bean(initMethod = "start", destroyMethod = "stop")
	WireMockServer stripeStub() {
		return new WireMockServer(WireMockConfiguration.options().dynamicPort());
	}

	@Bean
	DynamicPropertyRegistrar stripeProperties(WireMockServer stripeStub) {
		return registry -> {
			registry.add("tickethub.stripe.api-base", stripeStub::baseUrl);
			registry.add("tickethub.stripe.secret-key", () -> "dummy-test-key");
			registry.add("tickethub.stripe.webhook-secret", () -> WEBHOOK_SECRET);
		};
	}

}
