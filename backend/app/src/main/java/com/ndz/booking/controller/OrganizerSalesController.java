package com.ndz.booking.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.request.OrganizerBookingFilter;
import com.ndz.booking.dto.response.SalesResponses.EventSales;
import com.ndz.booking.dto.response.SalesResponses.OrganizerBookingPage;
import com.ndz.booking.dto.response.SalesResponses.OrganizerDashboard;
import com.ndz.booking.service.OrganizerSalesService;
import com.ndz.shared.export.CsvExporter;
import com.ndz.shared.util.CurrentUser;

/** Số liệu bán vé và đơn hàng của organizer (màn #11, #14, #25; overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerSalesController {

	private final OrganizerSalesService service;

	OrganizerSalesController(OrganizerSalesService service) {
		this.service = service;
	}

	@Operation(operationId = "getEventSales", summary = "Số liệu bán vé của một sự kiện: theo hạng vé, theo ngày")
	@ApiResponse(responseCode = "200", description = "Số liệu", content = @Content(schema = @Schema(implementation = EventSales.class)))
	@ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND (không có hoặc không phải sự kiện của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/events/{eventId}/sales")
	EventSales eventSales(@PathVariable UUID eventId) {
		return service.eventSales(CurrentUser.require().id(), eventId);
	}

	@Operation(operationId = "getOrganizerDashboard", summary = "Tổng quan doanh thu 7 / 30 / 90 ngày gần nhất")
	@GetMapping("/dashboard")
	OrganizerDashboard dashboard(@RequestParam(defaultValue = "30") int days) {
		return service.dashboard(CurrentUser.require().id(), days);
	}

	@Operation(operationId = "listOrganizerBookings", summary = "Đơn hàng của các sự kiện của tôi")
	@GetMapping(path = "/bookings", produces = "application/json")
	OrganizerBookingPage bookings(@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) List<String> status, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.bookings(CurrentUser.require().id(), new OrganizerBookingFilter(eventId, status, from, to, q), page, size);
	}

	@Operation(operationId = "exportOrganizerBookings", summary = "Xuất CSV đơn hàng theo bộ lọc (tối đa 10.000 dòng)")
	@ApiResponse(responseCode = "422", description = "EXPORT_TOO_LARGE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@Hidden
	@GetMapping(path = "/bookings", produces = CsvExporter.MEDIA_TYPE)
	String exportBookings(@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) List<String> status, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String q) {
		return service.exportBookings(CurrentUser.require().id(), new OrganizerBookingFilter(eventId, status, from, to, q));
	}

}
