package com.ndz.catalog.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Đặt mức phí nền tảng mới (BR-06). Kiểm khoảng 0–2000 bps và mốc không ở quá khứ ở service (mã lỗi riêng). */
@Schema(name = "PlatformFeeRequest")
public record PlatformFeeRequest(
		@Schema(description = "Phí theo phần vạn: 500 = 5%", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Integer feeBps,
		@Schema(description = "Áp cho sự kiện publish từ thời điểm này", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Instant effectiveFrom) {
}
