package com.ndz.ticket.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.shared.util.CurrentUser;
import com.ndz.ticket.dto.request.BulkResendRequest;
import com.ndz.ticket.dto.response.ResendResponses.BulkResult;
import com.ndz.ticket.service.OrganizerTicketResendService;

/** Gửi lại email vé cho các đơn đã chọn, chỉ của chính organizer (#27, overview.md §11.1). */
@RestController
@RequestMapping("/api/v1/organizer/bookings")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerTicketResendController {

	private final OrganizerTicketResendService service;

	OrganizerTicketResendController(OrganizerTicketResendService service) {
		this.service = service;
	}

	@Operation(operationId = "bulkResendTickets",
			summary = "Gửi lại email vé cho các đơn đã chọn; đơn không phải của mình bị bỏ qua kèm lý do")
	@PostMapping("/bulk-resend-tickets")
	BulkResult resend(@Valid @RequestBody BulkResendRequest request) {
		return service.resend(CurrentUser.require().id(), request.bookingIds());
	}

}
