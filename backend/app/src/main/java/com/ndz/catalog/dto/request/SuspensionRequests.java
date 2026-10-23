package com.ndz.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request tạm dừng bán / đình chỉ của Admin (BR-13). Lý do bắt buộc. */
public final class SuspensionRequests {

	private SuspensionRequests() {
	}

	@Schema(name = "SuspendRequest")
	public record Suspend(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 500) String reason) {
	}

	@Schema(name = "BulkSuspendRequest")
	public record BulkSuspend(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 100) List<UUID> ids,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 500) String reason) {
	}

}
