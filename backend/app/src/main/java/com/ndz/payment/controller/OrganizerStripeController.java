package com.ndz.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.payment.dto.response.StripeAccountResponses.StripeAccountResponse;
import com.ndz.payment.dto.response.StripeAccountResponses.StripeLinkResponse;
import com.ndz.payment.service.OrganizerStripeService;
import com.ndz.shared.util.CurrentUser;

/** Kết nối Stripe của organizer (màn #26, overview.md §7.6, §11.2). */
@RestController
@RequestMapping("/api/v1/organizer/stripe")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerStripeController {

	private final OrganizerStripeService service;

	OrganizerStripeController(OrganizerStripeService service) {
		this.service = service;
	}

	@Operation(operationId = "createStripeOnboardingLink",
			summary = "Tạo Express account nếu chưa có, trả link onboarding Stripe (BR-05)")
	@ApiResponse(responseCode = "200", description = "Link onboarding",
			content = @Content(schema = @Schema(implementation = StripeLinkResponse.class)))
	@ApiResponse(responseCode = "409", description = "ORGANIZER_NOT_APPROVED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/onboarding-link")
	StripeLinkResponse onboardingLink() {
		return service.onboardingLink(CurrentUser.require().id());
	}

	@Operation(operationId = "getStripeAccount", summary = "Trạng thái kết nối Stripe và số dư")
	@GetMapping("/account")
	StripeAccountResponse account() {
		return service.account(CurrentUser.require().id());
	}

	@Operation(operationId = "createStripeLoginLink", summary = "Link vào Express Dashboard")
	@ApiResponse(responseCode = "200", description = "Link", content = @Content(schema = @Schema(implementation = StripeLinkResponse.class)))
	@ApiResponse(responseCode = "409", description = "STRIPE_NOT_CONNECTED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/login-link")
	StripeLinkResponse loginLink() {
		return service.loginLink(CurrentUser.require().id());
	}

}
