package com.ndz.account.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request của API hồ sơ cá nhân (overview.md §6.6, §11.2). */
public final class ProfileRequests {

	private ProfileRequests() {
	}

	/** Gửi đủ cả ba trường; trường để trống là xoá giá trị đang lưu. */
	@Schema(name = "UpdateProfileRequest")
	public record UpdateProfile(
			@Schema(description = "0… hoặc +84…, 10 số; khoảng trắng, dấu chấm, gạch nối được bỏ qua")
			@Size(max = 20) String phone,
			LocalDate dateOfBirth,
			@Schema(description = "Tỉnh / thành (đơn vị hành chính cấp 1)") UUID provinceId) {
	}

	@Schema(name = "DeleteAccountRequest")
	public record DeleteAccount(
			@Schema(description = "Email của tài khoản, gõ lại để xác nhận", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotBlank @Size(max = 320) String confirmEmail) {
	}

}
