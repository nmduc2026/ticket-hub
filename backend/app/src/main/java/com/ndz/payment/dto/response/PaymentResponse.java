package com.ndz.payment.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Payment")
public record PaymentResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID paymentId,
		@Schema(description = "Dùng với Stripe Payment Element ở trình duyệt; không lưu, không log",
				requiredMode = Schema.RequiredMode.REQUIRED) String clientSecret,
		@Schema(description = "Tổng tiền, đơn vị nhỏ nhất của tiền tệ (VND không nhân 100)",
				requiredMode = Schema.RequiredMode.REQUIRED) long amountMinor,
		@Schema(description = "Phí nền tảng đã gồm trong tổng tiền, không hoàn lại (BR-06, BR-07)",
				requiredMode = Schema.RequiredMode.REQUIRED) long platformFeeMinor,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {
}
