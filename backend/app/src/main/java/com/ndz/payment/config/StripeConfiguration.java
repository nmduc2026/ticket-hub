package com.ndz.payment.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.client.StripeResilience;
import io.micrometer.core.instrument.MeterRegistry;
import com.stripe.StripeClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfiguration {

	private static final Logger log = LoggerFactory.getLogger(StripeConfiguration.class);

	/** Khoá giữ chỗ để app vẫn khởi động được khi chưa cấu hình Stripe (test, CI); lời gọi thật sẽ bị Stripe từ chối. */
	private static final String UNCONFIGURED_KEY = "unconfigured";

	@Bean
	public StripeClient stripeClient(StripeProperties properties) {
		boolean configured = StringUtils.hasText(properties.secretKey());
		if (!configured) {
			log.warn("tickethub.stripe.secret-key chưa đặt: các lời gọi Stripe sẽ thất bại");
		}
		int timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
		var builder = StripeClient.builder().setApiKey(configured ? properties.secretKey() : UNCONFIGURED_KEY)
				.setConnectTimeout(timeoutMillis).setReadTimeout(timeoutMillis)
				// Retry do Resilience4j quyết định (StripeResilience), không để SDK tự retry ngầm.
				.setMaxNetworkRetries(0);
		if (StringUtils.hasText(properties.apiBase())) {
			builder.setApiBase(properties.apiBase());
		}
		return builder.build();
	}

	@Bean
	public StripeResilience stripeResilience(StripeProperties properties, MeterRegistry metrics) {
		return new StripeResilience(properties.resilience(), metrics);
	}

	@Bean
	public StripeGateway stripeGateway(StripeClient client, StripeResilience resilience, StripeProperties properties,
			Clock clock) {
		return new StripeGateway(client, resilience, properties.webhookSecret(), properties.webhookTolerance(), clock);
	}

}
