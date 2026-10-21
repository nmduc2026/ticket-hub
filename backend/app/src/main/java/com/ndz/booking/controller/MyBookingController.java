package com.ndz.booking.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.response.BookingResponses.SummaryResponse;
import com.ndz.booking.mapper.BookingMapper;
import com.ndz.booking.service.BookingService;
import com.ndz.shared.util.CurrentUser;

/** Đơn của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/me/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
class MyBookingController {

	private final BookingService service;
	private final BookingMapper mapper;

	MyBookingController(BookingService service, BookingMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "listMyBookings", summary = "Đơn của tôi, mới tạo trước")
	@GetMapping
	List<SummaryResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.listMine(CurrentUser.require().id(), page, size).stream().map(mapper::toSummary).toList();
	}

}
