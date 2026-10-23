package com.ndz.booking.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.booking.dto.response.BookingResponses.RefundProgress;
import com.ndz.booking.service.EventCancellationService;

/** Tiến độ hoàn tiền khi huỷ sự kiện (màn #34, BR-08). */
@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
class AdminEventRefundController {

	private final EventCancellationService cancellations;

	AdminEventRefundController(EventCancellationService cancellations) {
		this.cancellations = cancellations;
	}

	@Operation(operationId = "getEventRefundProgress", summary = "Tiến độ hoàn tiền của sự kiện bị huỷ")
	@GetMapping("/{eventId}/refund-progress")
	RefundProgress refundProgress(@PathVariable UUID eventId) {
		return cancellations.refundProgress(eventId);
	}

}
