package com.ndz.catalog.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.request.BulkIdsRequest;
import com.ndz.catalog.dto.request.VenueRequest;
import com.ndz.catalog.dto.response.AdminVenueResponse;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.service.AdminVenueService;
import com.ndz.shared.util.CurrentUser;

/** Quản lý địa điểm của Admin (màn #16, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/admin/venues")
@PreAuthorize("hasRole('ADMIN')")
class AdminVenueController {

	private final AdminVenueService service;

	AdminVenueController(AdminVenueService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdminVenues", summary = "Danh sách địa điểm")
	@GetMapping
	List<AdminVenueResponse> list() {
		return service.list();
	}

	@Operation(operationId = "createVenue", summary = "Tạo địa điểm")
	@ApiResponse(responseCode = "422", description = "WARD_LEVEL_INVALID",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	AdminVenueResponse create(@Valid @RequestBody VenueRequest request) {
		return service.create(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "updateVenue", summary = "Sửa địa điểm")
	@ApiResponse(responseCode = "404", description = "VENUE_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{id}")
	AdminVenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
		return service.update(CurrentUser.require().id(), id, request);
	}

	@Operation(operationId = "bulkDeleteVenues", summary = "Xoá nhiều địa điểm; chỉ xoá được địa điểm chưa có sự kiện")
	@PostMapping("/bulk-delete")
	BulkResult bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
		return service.bulkDelete(CurrentUser.require().id(), request.ids());
	}

}
