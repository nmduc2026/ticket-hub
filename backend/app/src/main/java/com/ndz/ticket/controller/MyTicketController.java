package com.ndz.ticket.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.shared.util.CurrentUser;
import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;
import com.ndz.ticket.service.TicketQueryService;

/** Vé của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/me/tickets")
@PreAuthorize("hasRole('CUSTOMER')")
class MyTicketController {

	private final TicketQueryService service;

	MyTicketController(TicketQueryService service) {
		this.service = service;
	}

	@Operation(operationId = "listMyTickets", summary = "Vé của tôi, mới phát trước")
	@GetMapping
	List<TicketResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.myTickets(CurrentUser.require().id(), page, size);
	}

}
