package com.ndz.account.controller;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.account.dto.request.AdminAuditLogFilter;
import com.ndz.account.dto.response.AdminAuditLogResponses.AdminAuditLogPage;
import com.ndz.account.service.AdminAuditLogService;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.export.CsvExporter;
import com.ndz.shared.util.CurrentUser;

/** Nhật ký thao tác quản trị (màn #31, overview.md §13.4). */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
class AdminAuditLogController {

	private final AdminAuditLogService service;

	AdminAuditLogController(AdminAuditLogService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdminAuditLogs", summary = "Nhật ký thao tác quản trị, lọc theo người thao tác / hành động / thời gian")
	@GetMapping
	AdminAuditLogPage list(@RequestParam(required = false) UUID actor, @RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(new AdminAuditLogFilter(actor, action, from, to), page, size);
	}

	@Operation(operationId = "exportAdminAuditLogs", summary = "Xuất CSV nhật ký theo cùng bộ lọc (tối đa 10.000 dòng)")
	@ApiResponse(responseCode = "422", description = "EXPORT_TOO_LARGE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@Hidden
	@GetMapping(produces = CsvExporter.MEDIA_TYPE)
	String export(@RequestParam(required = false) UUID actor, @RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
		return service.export(CurrentUser.require().id(), new AdminAuditLogFilter(actor, action, from, to));
	}

}
