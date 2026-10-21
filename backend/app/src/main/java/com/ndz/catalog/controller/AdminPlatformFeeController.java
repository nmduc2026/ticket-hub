package com.ndz.catalog.controller;

import java.util.List;

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

import com.ndz.catalog.dto.request.PlatformFeeRequest;
import com.ndz.catalog.dto.response.PlatformFeeResponse;
import com.ndz.catalog.service.PlatformFeeService;
import com.ndz.shared.util.CurrentUser;

/** Phí nền tảng của Admin (màn #30, BR-06, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/admin/platform-fees")
@PreAuthorize("hasRole('ADMIN')")
class AdminPlatformFeeController {

	private final PlatformFeeService service;

	AdminPlatformFeeController(PlatformFeeService service) {
		this.service = service;
	}

	@Operation(operationId = "listPlatformFees", summary = "Lịch sử mức phí nền tảng, mới nhất trước")
	@GetMapping
	List<PlatformFeeResponse> history() {
		return service.history();
	}

	@Operation(operationId = "schedulePlatformFee", summary = "Đặt mức phí mới, áp cho sự kiện publish từ effectiveFrom")
	@ApiResponse(responseCode = "201", description = "Mức phí đã lưu",
			content = @Content(schema = @Schema(implementation = PlatformFeeResponse.class)))
	@ApiResponse(responseCode = "422", description = "FEE_OUT_OF_RANGE (ngoài 0–2000 bps) hoặc EFFECTIVE_FROM_IN_PAST",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PlatformFeeResponse schedule(@Valid @RequestBody PlatformFeeRequest request) {
		return service.schedule(CurrentUser.require().id(), request);
	}

}
