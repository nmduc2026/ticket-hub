package com.ndz.catalog.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminVenue")
public record AdminVenueResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID wardId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String wardName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int capacity) {
}
