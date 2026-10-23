package com.ndz.ticket.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.shared.util.CurrentUser;
import com.ndz.ticket.dto.request.CheckInRequest;
import com.ndz.ticket.dto.response.CheckInResponses.CheckInResponse;
import com.ndz.ticket.dto.response.CheckInResponses.KeySetResponse;
import com.ndz.ticket.service.CheckInService;

/** Check-in tại cổng cho organizer của sự kiện (overview.md §7.5, §11.2). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ORGANIZER')")
class CheckInController {

	private final CheckInService service;

	CheckInController(CheckInService service) {
		this.service = service;
	}

	@Operation(operationId = "checkIn", summary = "Check-in một vé (BR-09)",
			description = "Chỉ cho vào khi server xác nhận. Vé chuyển VALID → USED nguyên tử. Mở từ 3 giờ trước giờ diễn tới giờ kết thúc.")
	@ApiResponse(responseCode = "200", description = "Vé hợp lệ, đã chuyển sang USED",
			content = @Content(schema = @Schema(implementation = CheckInResponse.class)))
	@ApiResponse(responseCode = "403", description = "NOT_EVENT_OWNER (không phải organizer của sự kiện)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "TICKET_NOT_FOUND (QR sai chữ ký hoặc không có vé)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "TICKET_ALREADY_USED hoặc TICKET_REVOKED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422",
			description = "CHECKIN_NOT_OPEN (sớm hơn 3 giờ trước giờ diễn) hoặc EVENT_ENDED (sau giờ kết thúc)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/checkins")
	CheckInResponse checkIn(@Valid @RequestBody CheckInRequest request) {
		return service.checkIn(CurrentUser.require().id(), request.qrToken(), request.gate());
	}

	@Operation(operationId = "getCheckInKeys", summary = "Khoá công khai (JWKS) để xác minh chữ ký QR theo kid")
	@GetMapping("/checkin/keys")
	KeySetResponse keys() {
		return service.signingKeys();
	}

}
