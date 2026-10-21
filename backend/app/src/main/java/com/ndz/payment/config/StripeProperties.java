package com.ndz.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình Stripe (test mode). Khoá chỉ đến từ biến môi trường / K8s Secret, không commit.
 *
 * @param secretKey secret key {@code sk_test_...}; để trống thì mọi lời gọi Stripe thất bại
 * @param webhookSecret signing secret {@code whsec_...} của endpoint webhook
 * @param connectWebhookSecret signing secret riêng của endpoint webhook Connect ({@code account.updated})
 * @param connectReturnUrl trang organizer quay về sau onboarding Stripe (màn #26)
 * @param connectRefreshUrl trang mở lại khi link onboarding hết hạn
 * @param apiBase ghi đè địa chỉ API (stripe-mock hoặc WireMock khi test); để trống thì dùng api.stripe.com
 * @param timeout thời gian chờ tối đa cho một lời gọi Stripe (overview.md §15.1: 3 giây)
 * @param webhookTolerance độ lệch tối đa giữa giờ ký và giờ nhận webhook (AC-4: 5 phút)
 * @param resilience retry, circuit breaker và bulkhead quanh lời gọi Stripe (overview.md §15.1)
 */
@ConfigurationProperties("tickethub.stripe")
public record StripeProperties(String secretKey, String webhookSecret, String connectWebhookSecret,
		@DefaultValue("http://localhost:5173/organizer/payments?connect=return") String connectReturnUrl,
		@DefaultValue("http://localhost:5173/organizer/payments?connect=refresh") String connectRefreshUrl,
		String apiBase,
		@DefaultValue("3s") Duration timeout, @DefaultValue("5m") Duration webhookTolerance,
		@DefaultValue Resilience resilience) {

	/**
	 * Mặc định theo overview.md §15.1.
	 *
	 * @param retryAttempts số lần gọi tối đa (kể cả lần đầu) khi lỗi tạm thời và lời gọi có idempotency key
	 * @param retryBackoff chờ trước lần thử lại đầu tiên (200ms)
	 * @param retryMultiplier hệ số nhân giữa các lần chờ (2)
	 * @param retryJitter độ lệch ngẫu nhiên tương đối của thời gian chờ (0.5 = ±50%)
	 * @param failureRateThreshold phần trăm lỗi trong cửa sổ để mở breaker (50)
	 * @param slidingWindowSize số lần gọi gần nhất để tính tỉ lệ lỗi (20)
	 * @param openDuration thời gian breaker mở trước khi half-open (30s)
	 * @param halfOpenCalls số lời gọi thử khi half-open (5)
	 * @param maxConcurrentCalls số lời gọi Stripe đồng thời tối đa mỗi pod (20)
	 */
	public record Resilience(@DefaultValue("3") int retryAttempts, @DefaultValue("200ms") Duration retryBackoff,
			@DefaultValue("2") double retryMultiplier, @DefaultValue("0.5") double retryJitter,
			@DefaultValue("50") float failureRateThreshold, @DefaultValue("20") int slidingWindowSize,
			@DefaultValue("30s") Duration openDuration, @DefaultValue("5") int halfOpenCalls,
			@DefaultValue("20") int maxConcurrentCalls) {

		/** Giá trị mặc định theo §15.1, cho code dựng cấu hình bằng tay (test). */
		public static Resilience defaults() {
			return new Resilience(3, Duration.ofMillis(200), 2, 0.5, 50, 20, Duration.ofSeconds(30), 5, 20);
		}

	}

}
