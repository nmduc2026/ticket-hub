package com.ndz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

/**
 * Giả lập API Stripe bằng WireMock cho test tích hợp (biên ngoài, không phải thứ ta sở hữu). Mọi test dùng chung một
 * context nên chung một server; mỗi test tự {@code resetAll()} và dựng stub của mình.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StripeStubConfiguration {

	/** Secret ký webhook mà test dùng để tự ký payload. */
	public static final String WEBHOOK_SECRET = "dummy-webhook-secret";

	/** Secret riêng của endpoint Connect (khác secret của endpoint platform, AC-2). */
	public static final String CONNECT_WEBHOOK_SECRET = "dummy-connect-webhook-secret";

	/**
	 * Tên transformer dùng cho stub trả về nhiều lần hoàn trong một test (vd. huỷ sự kiện): mỗi response thay id cố
	 * định trong body bằng một id duy nhất theo {@code Idempotency-Key} của request ({@code refund-{refundId}}), để
	 * không vi phạm {@code uq_refunds_stripe_refund} khi nhiều lần hoàn được gọi trong cùng một test.
	 */
	public static final String UNIQUE_REFUND_ID_TRANSFORMER = "unique-refund-id";

	@Bean(initMethod = "start", destroyMethod = "stop")
	WireMockServer stripeStub() {
		return new WireMockServer(
				WireMockConfiguration.options().dynamicPort().extensions(new UniqueRefundIdTransformer()));
	}

	@Bean
	DynamicPropertyRegistrar stripeProperties(WireMockServer stripeStub) {
		return registry -> {
			registry.add("tickethub.stripe.api-base", stripeStub::baseUrl);
			registry.add("tickethub.stripe.secret-key", () -> "dummy-test-key");
			registry.add("tickethub.stripe.webhook-secret", () -> WEBHOOK_SECRET);
			registry.add("tickethub.stripe.connect-webhook-secret", () -> CONNECT_WEBHOOK_SECRET);
			// Hàng đợi hoàn tiền: test gọi trực tiếp, không để job tự chạy; tốc độ cao để test 1.000 đơn nhanh.
			registry.add("tickethub.payment.refund-queue-interval", () -> "1h");
			registry.add("tickethub.payment.refund-queue-rate", () -> "1000");
			registry.add("tickethub.payment.refund-queue-batch-size", () -> "200");
		};
	}

	/** @see #UNIQUE_REFUND_ID_TRANSFORMER */
	private static final class UniqueRefundIdTransformer implements ResponseDefinitionTransformerV2 {

		@Override
		public String getName() {
			return UNIQUE_REFUND_ID_TRANSFORMER;
		}

		@Override
		public boolean applyGlobally() {
			return false; // chỉ áp dụng cho stub khai báo withTransformers(UNIQUE_REFUND_ID_TRANSFORMER)
		}

		@Override
		public ResponseDefinition transform(ServeEvent serveEvent) {
			String idempotencyKey = serveEvent.getRequest().getHeader("Idempotency-Key");
			String id = "re_" + (idempotencyKey == null ? serveEvent.getId() : idempotencyKey);
			String body = serveEvent.getResponseDefinition().getBody().replace("re_cancel", id);
			return ResponseDefinitionBuilder.like(serveEvent.getResponseDefinition()).withBody(body).build();
		}

	}

}
