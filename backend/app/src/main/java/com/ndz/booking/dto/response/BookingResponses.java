package com.ndz.booking.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response của API đặt vé. */
public final class BookingResponses {

	private BookingResponses() {
	}

	@Schema(name = "BookingHold")
	public record HoldResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(description = "Hạn giữ ghế", requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
			@Schema(description = "Tổng tiền, đơn vị nhỏ nhất của tiền tệ",
					requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {
	}

	@Schema(name = "BookingItem")
	public record Item(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID seatId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sectionCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String row,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID tierId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tierName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor) {
	}

	@Schema(name = "BookingSummary")
	public record SummaryResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(description = "PENDING_PAYMENT, CONFIRMED, EXPIRED, CANCELLED, REFUND_REQUESTED, REFUNDED hoặc REFUND_FAILED",
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(description = "Tổng tiền, đơn vị nhỏ nhất của tiền tệ",
					requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(description = "REQUESTED, REFUNDED hoặc FAILED khi đơn đã yêu cầu hoàn; ngược lại vắng mặt",
					nullable = true) String refundStatus,
			@Schema(description = "Số tiền khách nhận lại đã chốt khi yêu cầu hoàn, đơn vị nhỏ nhất của tiền tệ",
					nullable = true) Long refundAmountMinor,
			@Schema(description = "Số vé của đơn", requiredMode = Schema.RequiredMode.REQUIRED) int ticketCount,
			@Schema(description = "Phí nền tảng trong tổng tiền, không hoàn lại (BR-07)",
					requiredMode = Schema.RequiredMode.REQUIRED) long platformFeeMinor,
			@Schema(description = "Số tiền nhận lại nếu hoàn ngay; chỉ có với đơn CONFIRMED", nullable = true)
			Long refundableAmountMinor,
			@Schema(description = "Hạn cuối yêu cầu hoàn; chỉ có với đơn CONFIRMED", nullable = true)
			Instant refundDeadline) {
	}

	@Schema(name = "BookingRefund")
	public record RefundResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(description = "Luôn là REFUND_REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(description = "Số tiền khách nhận lại: tổng đã trả trừ phí nền tảng (BR-07)",
					requiredMode = Schema.RequiredMode.REQUIRED) long refundAmountMinor) {
	}

	@Schema(name = "Booking")
	public record BookingResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(description = "PENDING_PAYMENT, CONFIRMED, EXPIRED, CANCELLED, REFUND_REQUESTED, REFUNDED hoặc REFUND_FAILED",
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Item> items) {
	}

}
