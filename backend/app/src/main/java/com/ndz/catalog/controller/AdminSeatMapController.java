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

import com.ndz.catalog.dto.request.SeatMapRequest;
import com.ndz.catalog.dto.response.AdminSeatMapResponses.Summary;
import com.ndz.catalog.dto.response.VenueResponses.SeatMapResponse;
import com.ndz.catalog.service.AdminSeatMapService;
import com.ndz.shared.util.CurrentUser;

/** Quản lý sơ đồ ghế của Admin (màn #16, #17, ADR 0030, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/admin/seat-maps")
@PreAuthorize("hasRole('ADMIN')")
class AdminSeatMapController {

	private final AdminSeatMapService service;

	AdminSeatMapController(AdminSeatMapService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdminSeatMaps", summary = "Danh sách sơ đồ ghế, lọc theo địa điểm")
	@GetMapping
	List<Summary> list(@RequestParam(required = false) UUID venueId) {
		return service.list(venueId);
	}

	@Operation(operationId = "getAdminSeatMap", summary = "Chi tiết sơ đồ: khu (tham số), phần tử không bán")
	@ApiResponse(responseCode = "404", description = "SEAT_MAP_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{id}")
	SeatMapResponse get(@PathVariable UUID id) {
		return service.get(id);
	}

	@Operation(operationId = "createAdminSeatMap", summary = "Tạo sơ đồ ghế version 1; ghế sinh từ tham số khu")
	@ApiResponse(responseCode = "422", description = "SEAT_MAP_INVALID, SEAT_MAP_NAME_DUPLICATE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SeatMapResponse create(@Valid @RequestBody SeatMapRequest request) {
		return service.create(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "updateAdminSeatMap", summary = "Lưu sơ đồ: thay khu và phần tử, sinh lại ghế")
	@ApiResponse(responseCode = "409", description = "SEAT_MAP_LOCKED, SEAT_MAP_BEING_EDITED, SECTION_IN_USE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{id}")
	SeatMapResponse update(@PathVariable UUID id, @Valid @RequestBody SeatMapRequest request) {
		return service.update(CurrentUser.require().id(), id, request);
	}

	@Operation(operationId = "copyAdminSeatMap", summary = "Tạo bản sao version mới (chưa khoá) để sửa")
	@PostMapping("/{id}/copy")
	@ResponseStatus(HttpStatus.CREATED)
	SeatMapResponse copy(@PathVariable UUID id) {
		return service.copy(CurrentUser.require().id(), id);
	}

	@Operation(operationId = "deleteAdminSeatMap", summary = "Xoá sơ đồ chưa khoá và chưa sự kiện nào dùng")
	@ApiResponse(responseCode = "409", description = "SEAT_MAP_LOCKED, SEAT_MAP_IN_USE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		service.delete(CurrentUser.require().id(), id);
	}

}
