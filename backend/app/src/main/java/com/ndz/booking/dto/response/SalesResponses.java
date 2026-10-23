package com.ndz.booking.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Số liệu bán vé cho organizer (overview.md §6.2, màn #11, #14, #25). Tiền là đơn vị nhỏ nhất; ròng = (gộp − đã hoàn)
 * − (phí − phí của phần đã hoàn).
 */
public final class SalesResponses {

	private SalesResponses() {
	}

	@Schema(name = "EventSales")
	public record EventSales(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long grossMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long feeMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundedMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long netMinor,
			@Schema(description = "Vé đã bán trừ vé đã hoàn", requiredMode = Schema.RequiredMode.REQUIRED) int ticketsSold,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int capacity,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int checkedIn,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TierSales> byTier,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DaySales> byDay) {
	}

	@Schema(name = "TierSales")
	public record TierSales(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID tierId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tierName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sold,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int refunded,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int capacity,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long grossMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long feeMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundedMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int checkedIn) {
	}

	@Schema(name = "DaySales", description = "Theo ngày xác nhận đơn, giờ Việt Nam")
	public record DaySales(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long grossMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int tickets) {
	}

	@Schema(name = "OrganizerDashboard")
	public record OrganizerDashboard(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate from,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate to,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long grossMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long feeMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundedMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long netMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ticketsSold,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DaySales> byDay,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrganizerBooking> recentBookings) {
	}

	@Schema(name = "OrganizerBooking")
	public record OrganizerBooking(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ticketCount,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contactEmail,
			Instant confirmedAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
	}

	@Schema(name = "OrganizerBookingPage")
	public record OrganizerBookingPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrganizerBooking> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {
	}

}
