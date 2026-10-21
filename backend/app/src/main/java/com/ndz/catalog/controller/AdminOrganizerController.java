package com.ndz.catalog.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.catalog.dto.request.OrganizerReviewRequests.BulkApproveRequest;
import com.ndz.catalog.dto.request.OrganizerReviewRequests.RejectRequest;
import com.ndz.catalog.dto.response.OrganizerResponses.BulkApproveResult;
import com.ndz.catalog.dto.response.OrganizerResponses.OrganizerPage;
import com.ndz.catalog.dto.response.OrganizerResponses.ReviewResult;
import com.ndz.catalog.entity.enums.OrganizerStatus;
import com.ndz.catalog.service.OrganizerAdminService;
import com.ndz.catalog.dto.request.SuspensionRequests;
import com.ndz.catalog.dto.response.SuspensionResponses.OrganizerSuspension;
import com.ndz.catalog.service.EventSuspensionService;
import com.ndz.shared.util.CurrentUser;

/** Admin duyệt hồ sơ organizer (overview.md §11.2, §7.6). */
@RestController
@RequestMapping("/api/v1/admin/organizers")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrganizerController {

	private final OrganizerAdminService service;
	private final EventSuspensionService suspensions;

	AdminOrganizerController(OrganizerAdminService service, EventSuspensionService suspensions) {
		this.service = service;
		this.suspensions = suspensions;
	}

	@Operation(operationId = "listAdminOrganizers", summary = "Danh sách hồ sơ organizer, lọc theo trạng thái")
	@GetMapping
	OrganizerPage list(@RequestParam(required = false) OrganizerStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, page, size);
	}

	@Operation(operationId = "approveOrganizer", summary = "Duyệt hồ sơ organizer")
	@ApiResponse(responseCode = "200", description = "Đã duyệt",
			content = @Content(schema = @Schema(implementation = ReviewResult.class)))
	@ApiResponse(responseCode = "409", description = "Hồ sơ không còn chờ duyệt (ORGANIZER_NOT_PENDING)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/approve")
	ReviewResult approve(@PathVariable UUID id) {
		return service.approve(CurrentUser.require().id(), id);
	}

	@Operation(operationId = "rejectOrganizer", summary = "Từ chối hồ sơ organizer, kèm lý do")
	@ApiResponse(responseCode = "200", description = "Đã từ chối",
			content = @Content(schema = @Schema(implementation = ReviewResult.class)))
	@ApiResponse(responseCode = "409", description = "Hồ sơ không còn chờ duyệt (ORGANIZER_NOT_PENDING)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/reject")
	ReviewResult reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
		return service.reject(CurrentUser.require().id(), id, request.reason());
	}

	@Operation(operationId = "suspendOrganizer",
			summary = "Đình chỉ organizer, tạm dừng mọi sự kiện đang bán của họ (BR-13)")
	@ApiResponse(responseCode = "200", description = "Đã đình chỉ",
			content = @Content(schema = @Schema(implementation = OrganizerSuspension.class)))
	@ApiResponse(responseCode = "409", description = "ORGANIZER_STATE_INVALID (không ở APPROVED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/suspend")
	OrganizerSuspension suspend(@PathVariable UUID id, @Valid @RequestBody SuspensionRequests.Suspend request) {
		return suspensions.suspendOrganizer(CurrentUser.require().id(), id, request.reason());
	}

	@Operation(operationId = "reinstateOrganizer",
			summary = "Khôi phục organizer bị đình chỉ; sự kiện vẫn tạm dừng tới khi mở lại từng cái (BR-13)")
	@ApiResponse(responseCode = "200", description = "Đã khôi phục",
			content = @Content(schema = @Schema(implementation = OrganizerSuspension.class)))
	@ApiResponse(responseCode = "409", description = "ORGANIZER_STATE_INVALID (không bị đình chỉ)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/reinstate")
	OrganizerSuspension reinstate(@PathVariable UUID id, @Valid @RequestBody SuspensionRequests.Suspend request) {
		return suspensions.reinstateOrganizer(CurrentUser.require().id(), id, request.reason());
	}

	@Operation(operationId = "bulkApproveOrganizers", summary = "Duyệt nhiều hồ sơ organizer")
	@PostMapping("/bulk-approve")
	BulkApproveResult bulkApprove(@Valid @RequestBody BulkApproveRequest request) {
		return service.bulkApprove(CurrentUser.require().id(), request.ids());
	}

}
