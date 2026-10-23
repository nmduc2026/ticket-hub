package com.ndz.account.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response của API hồ sơ cá nhân (overview.md §6.6, §11.2). */
public final class ProfileResponses {

	private ProfileResponses() {
	}

	/** Email và tên lấy từ Keycloak (sửa ở trang tài khoản Keycloak); các trường còn lại app giữ. */
	@Schema(name = "Profile")
	public record ProfileResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
			String email,
			String fullName,
			String avatarUrl,
			String phone,
			LocalDate dateOfBirth,
			Province province) {
	}

	@Schema(name = "ProfileProvince")
	public record Province(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
	}

	@Schema(name = "AvatarUpload")
	public record AvatarResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String avatarUrl) {
	}

	@Schema(name = "UserSession")
	public record SessionResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
			String ipAddress,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastAccessAt,
			@Schema(description = "Phiên của trình duyệt đang gọi", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean current) {
	}

}
