package com.ndz.backend.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình Stripe (test mode). Khoá chỉ đến từ biến môi trường / K8s Secret, không commit.
 *
 * @param secretKey secret key {@code sk_test_...}; để trống thì mọi lời gọi Stripe thất bại
 * @param webhookSecret signing secret {@code whsec_...} của endpoint webhook
 * @param apiBase ghi đè địa chỉ API (stripe-mock hoặc WireMock khi test); để trống thì dùng api.stripe.com
 * @param timeout thời gian chờ tối đa cho một lời gọi Stripe (overview.md §15.1: 3 giây)
 * @param webhookTolerance độ lệch tối đa giữa giờ ký và giờ nhận webhook (AC-4: 5 phút)
 */
@ConfigurationProperties("tickethub.stripe")
public record StripeProperties(String secretKey, String webhookSecret, String apiBase,
		@DefaultValue("3s") Duration timeout, @DefaultValue("5m") Duration webhookTolerance) {
}
