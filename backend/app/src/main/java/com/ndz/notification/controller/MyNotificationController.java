package com.ndz.notification.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.notification.dto.request.UpdatePreferencesRequest;
import com.ndz.notification.dto.response.NotificationResponses.NotificationPage;
import com.ndz.notification.dto.response.NotificationResponses.PreferenceItem;
import com.ndz.notification.dto.response.NotificationResponses.UnreadCount;
import com.ndz.notification.service.InAppNotificationService;
import com.ndz.shared.util.CurrentUser;

/** Hộp thư thông báo và cài đặt nhận thông báo của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
class MyNotificationController {

	private final InAppNotificationService service;

	MyNotificationController(InAppNotificationService service) {
		this.service = service;
	}

	@Operation(operationId = "listMyNotifications", summary = "Hộp thư thông báo, mới nhất trước (theo con trỏ)")
	@GetMapping("/notifications")
	NotificationPage list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(required = false) UUID cursor, @RequestParam(defaultValue = "20") int size) {
		return service.list(CurrentUser.require().id(), unread, cursor, size);
	}

	@Operation(operationId = "getMyUnreadNotificationCount", summary = "Số thông báo chưa đọc cho chuông")
	@GetMapping("/notifications/unread-count")
	UnreadCount unreadCount() {
		return new UnreadCount(service.unreadCount(CurrentUser.require().id()));
	}

	@Operation(operationId = "markNotificationRead", summary = "Đánh dấu một thông báo đã đọc")
	@ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/notifications/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@PathVariable UUID notificationId) {
		service.markRead(CurrentUser.require().id(), notificationId);
	}

	@Operation(operationId = "markAllNotificationsRead", summary = "Đánh dấu mọi thông báo đã đọc")
	@PostMapping("/notifications/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead() {
		service.markAllRead(CurrentUser.require().id());
	}

	@Operation(operationId = "getMyNotificationPreferences", summary = "Cài đặt nhận thông báo theo loại")
	@GetMapping("/notification-preferences")
	List<PreferenceItem> preferences() {
		return service.preferences(CurrentUser.require().id());
	}

	@Operation(operationId = "updateMyNotificationPreferences", summary = "Lưu cài đặt nhận thông báo",
			description = "Loại bắt buộc (vé, hoàn tiền, huỷ sự kiện) không tắt được.")
	@ApiResponse(responseCode = "422", description = "NOTIFICATION_MANDATORY hoặc UNKNOWN_NOTIFICATION_TYPE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/notification-preferences")
	List<PreferenceItem> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
		return service.updatePreferences(CurrentUser.require().id(), request);
	}

}
