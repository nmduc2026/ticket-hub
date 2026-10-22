package com.ndz.queue.controller;

import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.queue.dto.response.QueueStatusResponse;
import com.ndz.queue.service.QueueService;
import com.ndz.shared.util.CurrentUser;

/** Phòng chờ ảo của sự kiện nhu cầu cao (overview.md §7.1, BR-04). */
@RestController
@RequestMapping("/api/v1/queue/{eventId}")
@PreAuthorize("hasRole('CUSTOMER')")
class QueueController {

	private final QueueService service;

	QueueController(QueueService service) {
		this.service = service;
	}

	@Operation(operationId = "joinQueue", summary = "Vào phòng chờ",
			description = "Mở từ 15 phút trước giờ mở bán. Vào lại không đổi vị trí; đang có lượt vào thì trả ADMITTED.")
	@ApiResponse(responseCode = "202", description = "Đã vào hàng chờ",
			content = @Content(schema = @Schema(implementation = QueueStatusResponse.class)))
	@ApiResponse(responseCode = "404", description = "QUEUE_NOT_ENABLED (sự kiện không dùng phòng chờ) hoặc EVENT_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "QUEUE_NOT_OPEN (chưa tới giờ mở phòng chờ) hoặc QUEUE_CLOSED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/join")
	@ResponseStatus(HttpStatus.ACCEPTED)
	QueueStatusResponse join(@PathVariable UUID eventId) {
		return service.join(eventId, CurrentUser.require().id());
	}

	@Operation(operationId = "getQueueStatus", summary = "Vị trí trong phòng chờ",
			description = "ADMITTED nếu còn lượt vào (dùng khi kết nối lại).")
	@ApiResponse(responseCode = "200", description = "Trạng thái hiện tại",
			content = @Content(schema = @Schema(implementation = QueueStatusResponse.class)))
	@ApiResponse(responseCode = "404", description = "QUEUE_NOT_JOINED, QUEUE_NOT_ENABLED hoặc EVENT_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "QUEUE_CLOSED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/status")
	QueueStatusResponse status(@PathVariable UUID eventId) {
		return service.status(eventId, CurrentUser.require().id());
	}

}
