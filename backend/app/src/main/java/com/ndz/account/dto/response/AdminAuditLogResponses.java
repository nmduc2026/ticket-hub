package com.ndz.account.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.shared.audit.AuditAction;

/** Nhật ký thao tác quản trị cho Admin (màn #31, overview.md §13.4). */
public final class AdminAuditLogResponses {

	private AdminAuditLogResponses() {
	}

	@Schema(name = "AdminAuditLogRow")
	public record AdminAuditLogRow(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID actorId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actorName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditAction action,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetType,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetId,
			Map<String, Object> beforeState,
			Map<String, Object> afterState,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
	}

	@Schema(name = "AdminAuditLogPage")
	public record AdminAuditLogPage(List<AdminAuditLogRow> items, long total, int page, int size) {
	}

}
