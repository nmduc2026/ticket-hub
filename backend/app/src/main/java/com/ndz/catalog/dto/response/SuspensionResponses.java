package com.ndz.catalog.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response tạm dừng / mở bán / đình chỉ của Admin (BR-13, overview.md §11.1 hàng loạt). */
public final class SuspensionResponses {

	private SuspensionResponses() {
	}

	@Schema(name = "EventStatusResult")
	public record EventStatusResult(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status) {
	}

	/** Kết quả từng id của thao tác hàng loạt; lỗi một id không làm hỏng các id khác. */
	@Schema(name = "BulkResult")
	public record BulkResult(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BulkItem> results) {
	}

	@Schema(name = "BulkItem")
	public record BulkItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ok,
			@Schema(description = "Mã lỗi nghiệp vụ khi ok = false") String code) {
	}

	@Schema(name = "OrganizerSuspension")
	public record OrganizerSuspension(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(description = "Số sự kiện vừa bị tạm dừng theo", requiredMode = Schema.RequiredMode.REQUIRED)
			int suspendedEvents) {
	}

}
