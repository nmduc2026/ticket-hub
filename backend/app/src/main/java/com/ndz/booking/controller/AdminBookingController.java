package com.ndz.booking.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.request.AdminBookingFilter;
import com.ndz.booking.dto.response.AdminBookingResponses.AdminBookingPage;
import com.ndz.booking.dto.response.BookingResponses.RefundResponse;
import com.ndz.booking.mapper.BookingMapper;
import com.ndz.booking.service.BookingAdminService;
import com.ndz.shared.export.CsvExporter;
import com.ndz.shared.util.CurrentUser;

/** Admin tìm đơn và hoàn thủ công đơn hoàn thất bại (overview.md §7.4, §11.2). */
@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
class AdminBookingController {

	private final BookingAdminService service;
	private final BookingMapper mapper;

	AdminBookingController(BookingAdminService service, BookingMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "listAdminBookings",
			summary = "Đơn toàn hệ thống, lọc theo trạng thái / organizer / cần đối soát / từ khoá")
	@GetMapping
	AdminBookingPage list(@RequestParam(required = false) List<String> status,
			@RequestParam(required = false) UUID organizerId, @RequestParam(required = false) Boolean needsReconcile,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(new AdminBookingFilter(status, organizerId, needsReconcile, q), page, size);
	}

	@Operation(operationId = "exportAdminBookings", summary = "Xuất CSV đơn theo cùng bộ lọc (tối đa 10.000 dòng)")
	@ApiResponse(responseCode = "422", description = "EXPORT_TOO_LARGE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@Hidden
	@GetMapping(produces = CsvExporter.MEDIA_TYPE)
	String export(@RequestParam(required = false) List<String> status,
			@RequestParam(required = false) UUID organizerId, @RequestParam(required = false) Boolean needsReconcile,
			@RequestParam(required = false) String q) {
		return service.export(CurrentUser.require().id(), new AdminBookingFilter(status, organizerId, needsReconcile, q));
	}

	@Operation(operationId = "retryBookingRefund", summary = "Hoàn thủ công đơn REFUND_FAILED bằng lần hoàn mới",
			description = "Tạo lần thử hoàn mới (refundId và khoá Stripe mới). Vé vẫn bị thu hồi.")
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "BOOKING_NOT_REFUNDABLE (đơn không ở trạng thái REFUND_FAILED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{bookingId}/refund")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RefundResponse retryRefund(@PathVariable UUID bookingId) {
		return mapper.toRefund(service.retryRefund(CurrentUser.require().id(), bookingId));
	}

}
