package com.ndz.catalog.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.OrganizerType;

/**
 * Hồ sơ đăng ký làm organizer (prototype #22). {@code legalName} là tên doanh nghiệp hoặc họ tên; {@code legalId} là
 * mã số thuế (10 hoặc 13 số, doanh nghiệp) hoặc số CCCD (12 số, cá nhân), được kiểm ở service theo {@code type}.
 */
@Schema(name = "OrganizerApplyRequest")
public record OrganizerApplyRequest(
		@NotNull OrganizerType type,
		@NotBlank @Size(max = 120) String displayName,
		@NotBlank @Size(max = 200) String legalName,
		@NotBlank @Size(max = 30) String legalId,
		@NotBlank @Email @Size(max = 200) String contactEmail,
		@NotBlank @Size(max = 20) String contactPhone,
		@Size(max = 300) String website,
		@NotBlank @Size(max = 2000) String introduction,
		@Size(max = 50) String eventScale) {

}
