package com.ndz.payment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cấu hình thanh toán chỉ đọc cho Admin (màn #36, overview.md §11.2, §13.4). */
public final class AdminStripeConfigResponses {

	private AdminStripeConfigResponses() {
	}

	@Schema(name = "AdminPaymentConfig")
	public record PaymentConfig(
			@Schema(description = "test, live hoặc not_configured", requiredMode = Schema.RequiredMode.REQUIRED) String mode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String secretKeyMasked,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String apiVersion,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String webhookEndpoint,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String connectWebhookEndpoint,
			@Schema(description = "Tỉ lệ webhook xử lý thành công trong 24 giờ qua, 0-100",
					requiredMode = Schema.RequiredMode.REQUIRED) double successRate24h,
			@Schema(description = "Số organizer theo trạng thái Connect: none, incomplete, pending, active, restricted",
					requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Integer> organizersByState) {
	}

	@Schema(name = "StripeDeliveryFailure")
	public record DeliveryFailure(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pendingWebhooks) {
	}

	@Schema(name = "StripeDeliveryFailurePage")
	public record DeliveryFailurePage(List<DeliveryFailure> items) {
	}

	@Schema(name = "StripeReprocessResult")
	public record ReprocessResult(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
			@Schema(description = "false khi event này đã xử lý từ trước, không đổi gì thêm",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean processed) {
	}

	@Schema(name = "StripeWebhookCheckResult")
	public record WebhookCheckResult(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int statusCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ok,
			String error) {
	}

}
