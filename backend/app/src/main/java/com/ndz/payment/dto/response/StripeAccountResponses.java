package com.ndz.payment.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response kết nối Stripe của organizer (màn #26, overview.md §7.6). */
public final class StripeAccountResponses {

	private StripeAccountResponses() {
	}

	@Schema(name = "StripeAccount")
	public record StripeAccountResponse(
			@Schema(description = "none, incomplete, pending, active hoặc restricted (overview.md §7.6)",
					allowableValues = { "none", "incomplete", "pending", "active", "restricted" },
					requiredMode = Schema.RequiredMode.REQUIRED) String state,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean chargesEnabled,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean payoutsEnabled,
			@Schema(description = "Mục Stripe còn yêu cầu bổ sung", requiredMode = Schema.RequiredMode.REQUIRED)
			List<String> requirements,
			@Schema(description = "Số dư khả dụng; trống khi chưa kết nối hoặc không đọc được") Long balanceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {
	}

	@Schema(name = "StripeLink")
	public record StripeLinkResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url,
			@Schema(description = "Chỉ có với link onboarding") Instant expiresAt) {
	}

}
