package com.ndz.booking.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Danh sách đơn toàn hệ thống cho Admin (màn #29, overview.md §11.2). */
public final class AdminBookingResponses {

	private AdminBookingResponses() {
	}

	@Schema(name = "AdminBookingRow")
	public record AdminBookingRowResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID organizerId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizerName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			int ticketCount,
			long totalMinor,
			String currency,
			String contactEmail,
			boolean needsReconcile,
			Instant confirmedAt,
			Instant createdAt) {
	}

	@Schema(name = "AdminBookingPage")
	public record AdminBookingPage(List<AdminBookingRowResponse> items, long total, int page, int size) {
	}

}
