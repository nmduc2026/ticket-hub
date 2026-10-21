package com.ndz.booking.controller;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.response.BookingResponses.RefundResponse;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.mapper.BookingMapper;
import com.ndz.booking.service.BookingAdminService;
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

	@Schema(name = "AdminBooking")
	record AdminBookingResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(description = "Email liên hệ lúc đặt vé", requiredMode = Schema.RequiredMode.REQUIRED) String userEmail,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(description = "Tổng tiền, đơn vị nhỏ nhất của tiền tệ",
					requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(description = "Lần hoàn thất bại gần nhất; vắng mặt nếu đơn chưa từng hoàn thất bại",
					nullable = true) Instant failedAt) {
	}

	@Operation(operationId = "listAdminBookings", summary = "Đơn theo trạng thái, vd. REFUND_FAILED cần hoàn thủ công")
	@GetMapping
	List<AdminBookingResponse> list(@RequestParam BookingStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.listByStatus(status, page, size).stream().map(booking -> new AdminBookingResponse(
				booking.bookingId(), booking.userEmail(), booking.eventTitle(), booking.totalMinor(),
				booking.currency(), booking.failedAt())).toList();
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
