package com.ndz.booking.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import com.ndz.booking.dto.response.BookingResponses.RefundResponse;
import com.ndz.booking.mapper.BookingMapper;
import com.ndz.booking.service.BookingRefundService;
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
	private final BookingRefundService refunds;
	private final BookingMapper mapper;

	BookingController(BookingService service, BookingRefundService refunds, BookingMapper mapper) {
		this.service = service;
		this.refunds = refunds;
		this.mapper = mapper;
	}

	@Operation(operationId = "createBooking", summary = "Giữ ghế, tạo đơn chờ thanh toán",
			description = "Bắt buộc header Idempotency-Key. Giữ ghế 10 phút (BR-03).")
	@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
			description = "Khoá chống gửi lặp (1-128 ký tự in được)", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "409",
			description = "SEAT_UNAVAILABLE, EVENT_NOT_ON_SALE hoặc PENDING_BOOKING_EXISTS (kèm bookingId của đơn đang chờ thanh toán)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503",
			description = "SEAT_HOLD_UNAVAILABLE: hệ thống giữ ghế tạm dừng (Redis không dùng được), thử lại sau Retry-After",
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

	@Operation(operationId = "requestBookingRefund", summary = "Yêu cầu hoàn cả đơn đã thanh toán (BR-07)",
			description = "Bắt buộc header Idempotency-Key. Vé bị thu hồi ngay (BR-09); yêu cầu hoàn không huỷ được.")
	@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
			description = "Khoá chống gửi lặp (1-128 ký tự in được)", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "202", description = "Đã nhận yêu cầu hoàn",
			content = @Content(schema = @Schema(implementation = RefundResponse.class)))
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "BOOKING_NOT_REFUNDABLE (đơn chưa thanh toán xong hoặc đã yêu cầu hoàn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "REFUND_WINDOW_CLOSED (còn dưới 48 giờ trước giờ diễn) hoặc IDEMPOTENCY_KEY_REUSED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{bookingId}/refund")
	@Idempotent(scope = "refund-booking")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RefundResponse refund(@PathVariable UUID bookingId) {
		return mapper.toRefund(refunds.requestRefund(CurrentUser.require().id(), bookingId));
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
