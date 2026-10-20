package com.ndz.backend.catalog.internal.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.catalog.internal.application.AdministrativeUnitQueryService;
import com.ndz.backend.catalog.internal.domain.CatalogViews.UnitRow;

/** Danh mục tỉnh / xã công khai, dùng cho dropdown lọc thành phố (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/administrative-units")
class AdministrativeUnitController {

	private final AdministrativeUnitQueryService service;

	AdministrativeUnitController(AdministrativeUnitQueryService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdministrativeUnits", summary = "Danh mục tỉnh / xã đang dùng")
	@GetMapping
	List<UnitResponse> list(@RequestParam(required = false) Integer level,
			@RequestParam(required = false) UUID parentId, @RequestParam(required = false, name = "q") String query) {
		return service.units(level, parentId, query).stream().map(UnitResponse::from).toList();
	}

	@Schema(name = "AdministrativeUnit")
	record UnitResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "TINH, THANH_PHO, PHUONG, XA hoặc DAC_KHU",
					requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(description = "1 = Tỉnh / Thành phố, 2 = Xã / Phường / Đặc khu",
					requiredMode = Schema.RequiredMode.REQUIRED) int level,
			UUID parentId) {

		static UnitResponse from(UnitRow row) {
			return new UnitResponse(row.id(), row.code(), row.name(), row.type(), row.level(), row.parentId());
		}

	}

}
