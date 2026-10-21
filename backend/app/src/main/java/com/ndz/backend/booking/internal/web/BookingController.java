package com.ndz.backend.booking.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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

import com.ndz.backend.booking.internal.application.BookingService;
import com.ndz.backend.booking.internal.domain.BookingViews.BookingItemView;
import com.ndz.backend.booking.internal.domain.BookingViews.BookingView;
import com.ndz.backend.shared.CurrentUser;
import com.ndz.backend.shared.idempotency.Idempotent;

/** Đặt vé: giữ ghế và xem đơn của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
class BookingController {

	private final BookingService service;

	BookingController(BookingService service) {
		this.service = service;
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
		return new HoldResponse(booking.id(), booking.expiresAt(), booking.totalMinor(), booking.currency());
	}

	@Operation(operationId = "getBooking", summary = "Trạng thái đơn của tôi")
	@ApiResponse(responseCode = "200", description = "Trạng thái đơn",
			content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{bookingId}")
	BookingResponse get(@PathVariable UUID bookingId) {
		return BookingResponse.from(service.get(CurrentUser.require().id(), bookingId));
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

	@Schema(name = "CreateBookingRequest")
	record CreateBookingRequest(@NotNull UUID eventId, @NotEmpty List<@NotNull UUID> seatIds) {
	}

	@Schema(name = "BookingHold")
	record HoldResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(description = "Hạn giữ ghế", requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
			@Schema(description = "Tổng tiền, đơn vị nhỏ nhất của tiền tệ",
					requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {
	}

	@Schema(name = "BookingItem")
	record Item(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID seatId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sectionCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String row,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID tierId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tierName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor) {

		static Item from(BookingItemView item) {
			return new Item(item.seatId(), item.sectionCode(), item.rowLabel(), item.seatNumber(), item.tierId(),
					item.tierName(), item.priceMinor());
		}

	}

	@Schema(name = "Booking")
	record BookingResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(description = "PENDING_PAYMENT, CONFIRMED, EXPIRED, CANCELLED, REFUND_REQUESTED, REFUNDED hoặc REFUND_FAILED",
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Item> items) {

		static BookingResponse from(BookingView booking) {
			return new BookingResponse(booking.id(), booking.eventId(), booking.status(), booking.expiresAt(),
					booking.totalMinor(), booking.currency(), booking.items().stream().map(Item::from).toList());
		}

	}

}
