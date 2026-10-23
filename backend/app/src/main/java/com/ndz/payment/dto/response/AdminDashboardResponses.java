package com.ndz.payment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tổng quan vận hành cho Admin (màn #35, overview.md §11.2). */
public final class AdminDashboardResponses {

	private AdminDashboardResponses() {
	}

	@Schema(name = "AdminDashboard")
	public record AdminDashboard(long grossMinor, long feeMinor, int ticketsSold, long eventsOnSale, Todo todo,
			List<OpeningSoonEvent> openingSoon) {
	}

	@Schema(name = "AdminDashboardTodo")
	public record Todo(long pendingOrganizers, long reconcile, long refundFailed, long suspendedEvents,
			long stripeRestricted) {
	}

	@Schema(name = "AdminDashboardOpeningSoonEvent")
	public record OpeningSoonEvent(UUID eventId, String title, UUID organizerId, Instant saleStartsAt) {
	}

}
