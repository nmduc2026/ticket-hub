package com.ndz.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CheckInRequest")
public record CheckInRequest(
		@Schema(description = "Chuỗi JWT trong mã QR của vé", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank @Size(max = 2048) String qrToken,
		@Schema(description = "Cổng soát vé, vd. A1", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank @Size(max = 50) String gate) {
}
