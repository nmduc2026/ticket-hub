package com.ndz.catalog.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.request.OrganizerApplyRequest;
import com.ndz.catalog.dto.response.OrganizerResponses.Application;
import com.ndz.catalog.dto.response.OrganizerResponses.ApplyResult;
import com.ndz.catalog.service.OrganizerApplicationService;
import com.ndz.shared.util.CurrentUser;

/** Đăng ký làm organizer của khách (overview.md §11.2, §7.6). */
@RestController
@RequestMapping("/api/v1/organizer")
@PreAuthorize("hasRole('CUSTOMER')")
class OrganizerApplicationController {

	private final OrganizerApplicationService service;

	OrganizerApplicationController(OrganizerApplicationService service) {
		this.service = service;
	}

	@Operation(operationId = "applyOrganizer", summary = "Nộp hồ sơ làm organizer (hoặc nộp lại sau khi bị từ chối)")
	@ApiResponse(responseCode = "201", description = "Hồ sơ ở trạng thái PENDING")
	@ApiResponse(responseCode = "409",
			description = "Đang chờ duyệt (APPLICATION_ALREADY_PENDING) hoặc đã là organizer (ALREADY_ORGANIZER)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422",
			description = "Mã số, số điện thoại hoặc website không hợp lệ (LEGAL_ID_INVALID, PHONE_INVALID, WEBSITE_INVALID)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/apply")
	@ResponseStatus(HttpStatus.CREATED)
	ApplyResult apply(@Valid @RequestBody OrganizerApplyRequest request) {
		return service.apply(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "getOrganizerApplication", summary = "Trạng thái hồ sơ organizer của tôi")
	@ApiResponse(responseCode = "404", description = "Chưa nộp hồ sơ (APPLICATION_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/application")
	Application application() {
		return service.myApplication(CurrentUser.require().id());
	}

}
