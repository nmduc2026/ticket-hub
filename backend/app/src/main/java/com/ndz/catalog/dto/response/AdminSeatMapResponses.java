package com.ndz.catalog.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Danh sách sơ đồ ghế cho Admin (màn #16, #17). Chi tiết dùng {@link VenueResponses.SeatMapResponse}. */
public final class AdminSeatMapResponses {

	private AdminSeatMapResponses() {
	}

	@Schema(name = "AdminSeatMapSummary")
	public record Summary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID venueId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int width,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int height,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean locked,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sectionCount,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int seatCount) {
	}

}
