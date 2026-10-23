package com.ndz.payment.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Chi tiết thanh toán của một đơn cho Admin (màn #29, overview.md §11.2): khoản thu, các lần hoàn, dòng thời gian Stripe. */
public final class AdminOrderPaymentResponses {

	private AdminOrderPaymentResponses() {
	}

	@Schema(name = "AdminOrderPayment")
	public record OrderPayment(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long applicationFeeMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String stripePaymentIntentId,
			@Schema(description = "Tài khoản Connect nhận tiền; trống khi thu trên platform") String destinationAccountId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderRefund> refunds,
			@Schema(description = "Cũ nhất trước", requiredMode = Schema.RequiredMode.REQUIRED) List<TimelineEntry> timeline) {
	}

	@Schema(name = "AdminOrderRefund")
	public record OrderRefund(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountMinor,
			String failureReason,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
	}

	/** {@code kind}: PAYMENT_CREATED, STRIPE_EVENT hoặc REFUND. */
	@Schema(name = "AdminOrderTimelineEntry")
	public record TimelineEntry(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,
			@Schema(allowableValues = { "PAYMENT_CREATED", "STRIPE_EVENT", "REFUND" },
					requiredMode = Schema.RequiredMode.REQUIRED) String kind,
			@Schema(description = "Loại event Stripe, lý do và trạng thái hoàn…", requiredMode = Schema.RequiredMode.REQUIRED) String label,
			@Schema(description = "Với event Stripe: chưa xử lý xong thì true", requiredMode = Schema.RequiredMode.REQUIRED) boolean unprocessed) {
	}

}
