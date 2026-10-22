package com.ndz.payment.controller;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.dto.response.AdminStripeConfigResponses.DeliveryFailurePage;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.PaymentConfig;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.ReprocessResult;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.WebhookCheckResult;
import com.ndz.payment.service.AdminStripeConfigService;

/** Cấu hình thanh toán chỉ đọc (màn #36, overview.md §11.2, §13.4). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminStripeConfigController {

	private final AdminStripeConfigService service;

	AdminStripeConfigController(AdminStripeConfigService service) {
		this.service = service;
	}

	@Operation(operationId = "getAdminPaymentConfig",
			summary = "Chế độ Stripe, phiên bản API, tỉ lệ webhook thành công 24 giờ, organizer theo trạng thái Connect")
	@GetMapping("/payment-config")
	PaymentConfig config() {
		return service.config();
	}

	@Operation(operationId = "listAdminStripeEvents", summary = "Event Stripe gửi webhook lỗi, chưa nhận được xác nhận")
	@GetMapping("/stripe-events")
	DeliveryFailurePage failedDeliveries() {
		return service.failedDeliveries();
	}

	@Operation(operationId = "reprocessAdminStripeEvent",
			summary = "Lấy lại event từ Stripe và xử lý lại; event đã xử lý thì không đổi gì")
	@PostMapping("/stripe-events/{id}/reprocess")
	ReprocessResult reprocess(@PathVariable String id) {
		return service.reprocess(id);
	}

	@Operation(operationId = "checkAdminStripeWebhook",
			summary = "Tự gửi payload thử có chữ ký tới endpoint webhook, trả mã phản hồi thật")
	@PostMapping("/stripe/webhook-check")
	WebhookCheckResult webhookCheck() {
		return service.webhookCheck();
	}

}
