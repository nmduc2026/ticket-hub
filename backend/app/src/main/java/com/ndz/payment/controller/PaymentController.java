package com.ndz.payment.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.dto.request.CreatePaymentRequest;
import com.ndz.payment.dto.response.PaymentResponse;
import com.ndz.payment.service.PaymentService;
import com.ndz.shared.idempotency.Idempotent;
import com.ndz.shared.util.CurrentUser;

/** Thanh toán booking của chính mình (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('CUSTOMER')")
class PaymentController {

	private final PaymentService service;

	PaymentController(PaymentService service) {
		this.service = service;
	}

	@Operation(operationId = "createPayment", summary = "Bắt đầu thanh toán, nhận clientSecret của Stripe",
			description = "Bắt buộc header Idempotency-Key. Còn dưới 90 giây giữ ghế thì từ chối (BR-03).")
	@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
			description = "Khoá chống gửi lặp (1-128 ký tự in được)", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND (không có hoặc không phải của bạn)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409",
			description = "HOLD_EXPIRING (còn dưới 90 giây), BOOKING_NOT_PAYABLE, PAYMENT_ALREADY_SUCCEEDED (đơn đã "
					+ "trả tiền, đang chờ xác nhận) hoặc ORGANIZER_PAYOUT_NOT_READY (Stripe Connect của organizer chưa "
					+ "charges_enabled)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "PAYMENT_PROVIDER_UNAVAILABLE (Stripe không phản hồi)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@Idempotent(scope = "create-payment")
	@ResponseStatus(HttpStatus.CREATED)
	PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
		return service.startPayment(CurrentUser.require().id(), request.bookingId());
	}

}
