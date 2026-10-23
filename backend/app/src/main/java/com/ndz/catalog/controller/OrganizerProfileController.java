package com.ndz.catalog.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.request.OrganizerApplyRequest;
import com.ndz.catalog.dto.response.OrganizerResponses.Application;
import com.ndz.catalog.service.OrganizerApplicationService;
import com.ndz.shared.util.CurrentUser;

/** Hồ sơ công khai và pháp lý của organizer (màn #27, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/organizer/profile")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerProfileController {

	private final OrganizerApplicationService service;

	OrganizerProfileController(OrganizerApplicationService service) {
		this.service = service;
	}

	@Operation(operationId = "getOrganizerProfile", summary = "Hồ sơ công khai và pháp lý của tôi")
	@GetMapping
	Application get() {
		return service.myApplication(CurrentUser.require().id());
	}

	@Operation(operationId = "updateOrganizerProfile",
			summary = "Sửa hồ sơ; đổi tên pháp lý / mã số / loại hình đưa về PENDING chờ Admin duyệt lại")
	@ApiResponse(responseCode = "422",
			description = "LEGAL_ID_INVALID, PHONE_INVALID hoặc WEBSITE_INVALID",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping
	Application update(@Valid @RequestBody OrganizerApplyRequest request) {
		return service.updateProfile(CurrentUser.require().id(), request);
	}

}
