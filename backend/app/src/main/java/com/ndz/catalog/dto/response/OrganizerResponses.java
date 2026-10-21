package com.ndz.catalog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.OrganizerStatus;
import com.ndz.catalog.entity.enums.OrganizerType;

/** Response của API đăng ký và duyệt organizer; entity không ra khỏi lớp service. */
public final class OrganizerResponses {

	private OrganizerResponses() {
	}

	@Schema(name = "OrganizerApplyResult")
	public record ApplyResult(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrganizerStatus status) {
	}

	/** Hồ sơ của chính người nộp, kèm nội dung đã khai để điền sẵn khi nộp lại. */
	@Schema(name = "OrganizerApplication")
	public record Application(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrganizerStatus status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant submittedAt,
			String rejectionReason,
			OrganizerType type,
			String displayName,
			String legalName,
			String legalId,
			String contactEmail,
			String contactPhone,
			String website,
			String introduction,
			String eventScale) {

	}

	/** Một hồ sơ trong danh sách của Admin; {@code email} là email liên hệ khai trong hồ sơ. */
	@Schema(name = "AdminOrganizer")
	public record AdminOrganizer(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
			String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrganizerStatus status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant submittedAt,
			OrganizerType type,
			String legalName,
			String legalId,
			String contactPhone,
			String website,
			String introduction,
			String eventScale,
			String rejectionReason) {

	}

	@Schema(name = "AdminOrganizerPage")
	public record OrganizerPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminOrganizer> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {

	}

	@Schema(name = "OrganizerReviewResult")
	public record ReviewResult(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrganizerStatus status) {
	}

	/** Kết quả duyệt hàng loạt: hồ sơ không ở {@code PENDING} (hoặc không tồn tại) được bỏ qua kèm mã lý do. */
	@Schema(name = "BulkApproveOrganizersResult")
	public record BulkApproveResult(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> approved,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Skipped> skipped) {

	}

	@Schema(name = "SkippedOrganizer")
	public record Skipped(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code) {

	}

}
