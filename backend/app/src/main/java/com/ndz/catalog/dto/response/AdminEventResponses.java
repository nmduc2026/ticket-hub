package com.ndz.catalog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.EventStatus;

/** Danh sách sự kiện toàn hệ thống cho Admin (màn #34, overview.md §11.2). */
public final class AdminEventResponses {

	private AdminEventResponses() {
	}

	@Schema(name = "AdminEventSummary")
	public record AdminEventSummary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventStatus status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID organizerId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizerName,
			Instant startsAt,
			Instant saleStartsAt) {
	}

	@Schema(name = "AdminEventPage")
	public record AdminEventPage(List<AdminEventSummary> items, long total, int page, int size) {
	}

}
