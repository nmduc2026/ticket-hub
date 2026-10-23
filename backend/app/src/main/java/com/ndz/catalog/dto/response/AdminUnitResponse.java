package com.ndz.catalog.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.AdministrativeUnitType;

@Schema(name = "AdminAdministrativeUnit")
public record AdminUnitResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdministrativeUnitType type,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int level,
		UUID parentId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveFrom) {
}
