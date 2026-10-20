package com.ndz.backend.account.internal.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.account.internal.application.UserProfileService;
import com.ndz.backend.account.internal.domain.UserProfile;
import com.ndz.backend.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final UserProfileService profiles;

	MeController(UserProfileService profiles) {
		this.profiles = profiles;
	}

	/** Hồ sơ của người đang đăng nhập (đã được đồng bộ ở interceptor trước khi vào đây). */
	@Operation(operationId = "getMe", summary = "Hồ sơ và role của người đang đăng nhập")
	@ApiResponse(responseCode = "200", description = "Hồ sơ người dùng",
			content = @Content(schema = @Schema(implementation = MeResponse.class)))
	@ApiResponse(responseCode = "401", description = "Chưa đăng nhập (UNAUTHENTICATED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping
	MeResponse me() {
		return MeResponse.from(profiles.get(CurrentUser.require().id()));
	}

	/** {@code roles} là role hiện có trong hồ sơ: CUSTOMER, ORGANIZER hoặc ADMIN. */
	record MeResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String fullName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String username,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled) {

		static MeResponse from(UserProfile profile) {
			return new MeResponse(profile.getUserId(), profile.getEmail(), profile.getFullName(),
					profile.getUsername(), profile.getRoles(), profile.isEnabled());
		}

	}

}
