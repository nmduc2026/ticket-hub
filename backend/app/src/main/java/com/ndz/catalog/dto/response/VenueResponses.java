package com.ndz.catalog.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;

/** Response của API địa điểm và sơ đồ ghế (overview.md §11.2). */
public final class VenueResponses {

	private VenueResponses() {
	}

	@Schema(name = "Venue")
	public record VenueResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String wardName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int capacity) {
	}

	@Schema(name = "SeatMap")
	public record SeatMapResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int width,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int height,
			@Schema(description = "true khi đã có sự kiện publish dùng: bất biến",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean locked,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Section> sections,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Element> elements) {
	}

	@Schema(name = "SeatMapSection")
	public record Section(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Số ghế của khu", requiredMode = Schema.RequiredMode.REQUIRED) int seatCount,
			@Schema(description = "Tham số hình học của khu (GRID / IMPORTED), nguyên văn từ shape_json",
					requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {
	}

	@Schema(name = "SeatMapElement")
	public record Element(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(description = "STAGE, AISLE, LABEL hoặc AREA", requiredMode = Schema.RequiredMode.REQUIRED) String type,
			String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {
	}

}
