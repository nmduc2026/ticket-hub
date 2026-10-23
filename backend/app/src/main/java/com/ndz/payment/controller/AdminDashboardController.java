package com.ndz.payment.controller;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.dto.response.AdminDashboardResponses.AdminDashboard;
import com.ndz.payment.service.AdminDashboardService;

/** Tổng quan vận hành cho Admin (màn #35, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
class AdminDashboardController {

	private final AdminDashboardService service;

	AdminDashboardController(AdminDashboardService service) {
		this.service = service;
	}

	@Operation(operationId = "getAdminDashboard",
			summary = "Doanh thu gộp, phí, vé bán, sự kiện đang bán và danh sách cần xử lý")
	@GetMapping
	AdminDashboard dashboard(@RequestParam(defaultValue = "30") int range) {
		return service.dashboard(range);
	}

}
