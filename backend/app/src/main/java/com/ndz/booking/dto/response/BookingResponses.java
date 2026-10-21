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
