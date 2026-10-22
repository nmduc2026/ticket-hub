package com.ndz.payment.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.dto.response.AdminOrderPaymentResponses.OrderPayment;
import com.ndz.payment.service.AdminOrderPaymentService;

/** Chi tiết thanh toán của đơn, có dòng thời gian Stripe (màn #29, overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderPaymentController {

	private final AdminOrderPaymentService service;

	AdminOrderPaymentController(AdminOrderPaymentService service) {
		this.service = service;
	}

	@Operation(operationId = "getAdminOrderPayment", summary = "Khoản thu, các lần hoàn và dòng thời gian Stripe của một đơn")
	@GetMapping("/{bookingId}/payment")
	OrderPayment payment(@PathVariable UUID bookingId) {
		return service.detail(bookingId);
	}

}
