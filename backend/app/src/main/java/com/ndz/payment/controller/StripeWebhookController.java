package com.ndz.payment.controller;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.service.StripeWebhookService;

/**
 * Nhận webhook của Stripe (overview.md §7.2, §13.1). Không có token và không có session: xác thực bằng chữ ký trên
 * raw body. Body nhận dưới dạng byte để giữ đúng từng byte Stripe đã ký.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/webhooks")
class StripeWebhookController {

	private final StripeWebhookService service;

	StripeWebhookController(StripeWebhookService service) {
		this.service = service;
	}

	@PostMapping("/stripe")
	@ResponseStatus(HttpStatus.OK)
	void stripe(@RequestBody byte[] body,
			@RequestHeader(name = "Stripe-Signature", required = false) String signature) {
		service.handle(new String(body, StandardCharsets.UTF_8), signature);
	}

}
