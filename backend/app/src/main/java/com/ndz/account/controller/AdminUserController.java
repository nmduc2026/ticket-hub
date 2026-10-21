package com.ndz.account.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.account.dto.response.AdminUserResponses.LockResult;
import com.ndz.account.dto.response.AdminUserResponses.UserDetail;
import com.ndz.account.dto.response.AdminUserResponses.UserPage;
import com.ndz.account.service.AdminUserService;
import com.ndz.shared.util.CurrentUser;

/** Admin quản lý người dùng (overview.md §11.2, §6.6). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController {

	private final AdminUserService service;

	AdminUserController(AdminUserService service) {
		this.service = service;
	}

	@Operation(operationId = "listAdminUsers", summary = "Danh sách người dùng, tìm theo tên / email, lọc theo role")
	@GetMapping
	UserPage list(@RequestParam(required = false, name = "q") String query,
			@RequestParam(required = false) String role, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(query, role, page, size);
	}

	@Operation(operationId = "getAdminUser", summary = "Chi tiết người dùng: hồ sơ, đơn hàng, vé, hoàn tiền, sự kiện")
	@ApiResponse(responseCode = "404", description = "Không có người dùng này (USER_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{id}")
	UserDetail detail(@PathVariable UUID id) {
		return service.detail(id);
	}

	@Operation(operationId = "lockUser", summary = "Khoá tài khoản (không áp dụng cho chính mình)")
	@ApiResponse(responseCode = "422", description = "Khoá chính mình (CANNOT_LOCK_SELF)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{id}/lock")
	LockResult lock(@PathVariable UUID id) {
		return service.lock(CurrentUser.require().id(), id);
	}

	@Operation(operationId = "unlockUser", summary = "Mở khoá tài khoản")
	@PostMapping("/{id}/unlock")
	LockResult unlock(@PathVariable UUID id) {
		return service.unlock(CurrentUser.require().id(), id);
	}

}
