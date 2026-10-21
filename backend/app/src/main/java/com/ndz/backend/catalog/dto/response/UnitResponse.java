package com.ndz.backend.catalog.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdministrativeUnit")
public record UnitResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(description = "TINH, THANH_PHO, PHUONG, XA hoặc DAC_KHU",
				requiredMode = Schema.RequiredMode.REQUIRED) String type,
		@Schema(description = "1 = Tỉnh / Thành phố, 2 = Xã / Phường / Đặc khu",
				requiredMode = Schema.RequiredMode.REQUIRED) int level,
		UUID parentId) {
}
