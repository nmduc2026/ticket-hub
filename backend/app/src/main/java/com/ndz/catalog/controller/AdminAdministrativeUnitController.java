package com.ndz.catalog.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.request.AdminUnitRequest;
import com.ndz.catalog.dto.response.AdminUnitResponse;
import com.ndz.catalog.service.AdminAdministrativeUnitService;
import com.ndz.shared.util.CurrentUser;

/** Quản lý đơn vị hành chính của Admin (màn #33, overview.md §11.2, §6.1). */
@RestController
@RequestMapping("/api/v1/admin/administrative-units")
@PreAuthorize("hasRole('ADMIN')")
class AdminAdministrativeUnitController {

	private final AdminAdministrativeUnitService service;

	AdminAdministrativeUnitController(AdminAdministrativeUnitService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdminAdministrativeUnits", summary = "Mọi đơn vị hành chính, kể cả đã tắt")
	@GetMapping
	List<AdminUnitResponse> list(@RequestParam(required = false) Integer level,
			@RequestParam(required = false) UUID parentId) {
		return service.list(level, parentId);
	}

	@Operation(operationId = "createAdministrativeUnit", summary = "Tạo đơn vị hành chính")
	@ApiResponse(responseCode = "422", description = "ADMIN_UNIT_PARENT_INVALID, ADMIN_UNIT_CODE_DUPLICATE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	AdminUnitResponse create(@Valid @RequestBody AdminUnitRequest request) {
		return service.create(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "updateAdministrativeUnit", summary = "Sửa đơn vị hành chính")
	@ApiResponse(responseCode = "404", description = "ADMIN_UNIT_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{id}")
	AdminUnitResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUnitRequest request) {
		return service.update(CurrentUser.require().id(), id, request);
	}

	@Operation(operationId = "disableAdministrativeUnit",
			summary = "Tắt đơn vị (không xoá cứng); chặn khi còn con hoạt động hoặc còn địa điểm")
	@ApiResponse(responseCode = "409", description = "ADMIN_UNIT_IN_USE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disable(@PathVariable UUID id) {
		service.disable(CurrentUser.require().id(), id);
	}

}
