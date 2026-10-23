package com.ndz.notification.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateNotificationPreferencesRequest")
public record UpdatePreferencesRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 20) List<@Valid Change> preferences) {

	@Schema(name = "NotificationPreferenceChange")
	public record Change(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inApp) {
	}

}
