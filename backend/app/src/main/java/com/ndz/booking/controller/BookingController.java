package com.ndz.booking.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.request.CreateBookingRequest;
import com.ndz.booking.dto.response.BookingResponses.BookingResponse;
import com.ndz.booking.dto.response.BookingResponses.HoldResponse;
import com.ndz.booking.mapper.BookingMapper;
import com.ndz.booking.service.BookingService;
import com.ndz.booking.dto.response.BookingViews.BookingView;
import com.ndz.shared.util.CurrentUser;
import com.ndz.shared.idempotency.Idempotent;

/** Đặt vé: giữ ghế và xem đơn của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
class BookingController {

	private final BookingService service;
	private final BookingMapper mapper;

	BookingController(BookingService service, BookingMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "createBooking", summary = "Giữ ghế, tạo đơn chờ thanh toán",
			description = "Bắt buộc header Idempotency-Key. Giữ ghế 10 phút (BR-03).")
	@ApiResponse(responseCode = "409", description = "SEAT_UNAVAILABLE, EVENT_NOT_ON_SALE hoặc PENDING_BOOKING_EXISTS",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422",
			description = "MAX_SEATS_EXCEEDED, TICKET_LIMIT_EXCEEDED, DUPLICATE_SEATS, EMAIL_REQUIRED hoặc IDEMPOTENCY_KEY_REUSED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@Idempotent(scope = "create-booking")
	@ResponseStatus(HttpStatus.CREATED)
	HoldResponse create(@Valid @RequestBody CreateBookingRequest request) {
		CurrentUser user = CurrentUser.require();
		BookingView booking = service.hold(user.id(), user.email(), request.eventId(), request.seatIds());
		return mapper.toHold(booking);
	}

	@Operation(operationId = "getBooking", summary = "Trạng thái đơn của tôi")
	@ApiResponse(responseCode = "200", description = "Trạng thái đơn",
			content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{bookingId}")
	BookingResponse get(@PathVariable UUID bookingId) {
		return mapper.toBooking(service.get(CurrentUser.require().id(), bookingId));
	}

	@Operation(operationId = "cancelBooking", summary = "Huỷ đơn đang chờ thanh toán và nhả ghế ngay (BR-12)")
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "BOOKING_NOT_CANCELLABLE (đã hết hạn, đã huỷ hoặc đã thanh toán)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping("/{bookingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@PathVariable UUID bookingId) {
		service.cancel(CurrentUser.require().id(), bookingId);
	}

}
