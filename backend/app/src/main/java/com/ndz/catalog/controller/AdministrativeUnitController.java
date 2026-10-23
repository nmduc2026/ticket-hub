package com.ndz.catalog.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.response.UnitResponse;
import com.ndz.catalog.mapper.VenueMapper;
import com.ndz.catalog.service.AdministrativeUnitQueryService;

/** Danh mục tỉnh / xã công khai, dùng cho dropdown lọc thành phố (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/administrative-units")
class AdministrativeUnitController {

	private final AdministrativeUnitQueryService service;
	private final VenueMapper mapper;

	AdministrativeUnitController(AdministrativeUnitQueryService service, VenueMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "listAdministrativeUnits", summary = "Danh mục tỉnh / xã đang dùng")
	@GetMapping
	List<UnitResponse> list(@RequestParam(required = false) Integer level,
			@RequestParam(required = false) UUID parentId, @RequestParam(required = false, name = "q") String query) {
		return service.units(level, parentId, query).stream().map(mapper::toUnit).toList();
	}

}
