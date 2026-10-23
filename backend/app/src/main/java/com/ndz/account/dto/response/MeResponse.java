package com.ndz.account.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code roles} là role hiện có trong hồ sơ: CUSTOMER, ORGANIZER hoặc ADMIN. */
public record MeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String fullName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String username,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled) {
}
