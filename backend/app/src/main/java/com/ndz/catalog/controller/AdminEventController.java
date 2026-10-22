package com.ndz.catalog.controller;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Hidden;
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

import com.ndz.catalog.dto.request.AdminEventFilter;
import com.ndz.catalog.dto.request.SuspensionRequests;
import com.ndz.catalog.dto.response.AdminEventResponses.AdminEventPage;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.dto.response.SuspensionResponses.EventStatusResult;
import com.ndz.catalog.entity.enums.EventStatus;
import com.ndz.catalog.service.AdminEventListService;
import com.ndz.catalog.service.EventSuspensionService;
import com.ndz.shared.export.CsvExporter;
import com.ndz.shared.util.CurrentUser;

/** Sự kiện của Admin (màn #34): danh sách, tạm dừng / mở bán lại / huỷ (BR-13, BR-08). */
@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
class AdminEventController {

	private final EventSuspensionService suspensions;
	private final AdminEventListService listing;

	AdminEventController(EventSuspensionService suspensions, AdminEventListService listing) {
		this.suspensions = suspensions;
		this.listing = listing;
	}

	@Operation(operationId = "listAdminEvents", summary = "Sự kiện mọi organizer, lọc theo trạng thái / organizer / thời gian / từ khoá")
	@GetMapping
	AdminEventPage list(@RequestParam(required = false) EventStatus status,
			@RequestParam(required = false) UUID organizerId, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return listing.list(new AdminEventFilter(status, organizerId, from, to, q), page, size);
	}

	@Operation(operationId = "exportAdminEvents", summary = "Xuất CSV sự kiện theo cùng bộ lọc (tối đa 10.000 dòng)")
	@ApiResponse(responseCode = "422", description = "EXPORT_TOO_LARGE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@Hidden
	@GetMapping(produces = CsvExporter.MEDIA_TYPE)
	String export(@RequestParam(required = false) EventStatus status,
			@RequestParam(required = false) UUID organizerId, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String q) {
		return listing.export(CurrentUser.require().id(), new AdminEventFilter(status, organizerId, from, to, q));
	}

	@Operation(operationId = "suspendEvent", summary = "Tạm dừng bán một sự kiện, kèm lý do (BR-13)")
	@ApiResponse(responseCode = "200", description = "Đã tạm dừng",
			content = @Content(schema = @Schema(implementation = EventStatusResult.class)))
	@ApiResponse(responseCode = "409", description = "INVALID_EVENT_TRANSITION (không ở PUBLISHED / ON_SALE)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/suspend")
	EventStatusResult suspend(@PathVariable UUID id, @Valid @RequestBody SuspensionRequests.Suspend request) {
		return suspensions.suspendEvent(CurrentUser.require().id(), id, request.reason());
	}

	@Operation(operationId = "cancelEvent",
			summary = "Huỷ sự kiện, hoàn 100% mọi đơn đã thanh toán qua hàng đợi (BR-08)")
	@ApiResponse(responseCode = "200", description = "Đã huỷ",
			content = @Content(schema = @Schema(implementation = EventStatusResult.class)))
	@ApiResponse(responseCode = "409", description = "INVALID_EVENT_TRANSITION (đã kết thúc / đã huỷ)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/cancel")
	EventStatusResult cancel(@PathVariable UUID id, @Valid @RequestBody SuspensionRequests.Suspend request) {
		return suspensions.cancelEvent(CurrentUser.require().id(), id, request.reason());
	}

	@Operation(operationId = "bulkSuspendEvents", summary = "Tạm dừng nhiều sự kiện; kết quả theo từng id")
	@PostMapping("/bulk-suspend")
	BulkResult bulkSuspend(@Valid @RequestBody SuspensionRequests.BulkSuspend request) {
		return suspensions.bulkSuspend(CurrentUser.require().id(), request.ids(), request.reason());
	}

	@Operation(operationId = "resumeEvent",
			summary = "Mở bán lại: ON_SALE nếu đã qua giờ mở bán, ngược lại PUBLISHED (BR-13)")
	@ApiResponse(responseCode = "200", description = "Đã mở lại",
			content = @Content(schema = @Schema(implementation = EventStatusResult.class)))
	@ApiResponse(responseCode = "409",
			description = "INVALID_EVENT_TRANSITION, ORGANIZER_SUSPENDED hoặc ORGANIZER_PAYOUT_NOT_READY",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/resume")
	EventStatusResult resume(@PathVariable UUID id) {
		return suspensions.resumeEvent(CurrentUser.require().id(), id);
	}

}
