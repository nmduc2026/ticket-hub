package com.ndz.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một mức phí trong lịch sử (BR-06); {@code current} là mức đang áp cho sự kiện publish lúc này. */
@Schema(name = "PlatformFee")
public record PlatformFeeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(description = "Phần vạn: 500 = 5%", requiredMode = Schema.RequiredMode.REQUIRED) int feeBps,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant effectiveFrom,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID createdBy,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean current) {
}
