package com.ndz.account.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response của API người dùng cho Admin (overview.md §6.6, §11.2). */
public final class AdminUserResponses {

	private AdminUserResponses() {
	}

	@Schema(name = "AdminUser")
	public record UserItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String fullName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
			@Schema(nullable = true) Instant lastLoginAt) {
	}

	@Schema(name = "AdminUserPage")
	public record UserPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UserItem> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {
	}

	@Schema(name = "AdminUserBooking")
	public record BookingItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ticketCount) {
	}

	@Schema(name = "AdminUserTicket")
	public record TicketItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ticketId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String venueName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String seat,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tierName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status) {
	}

	/** Đơn đã qua bước yêu cầu hoàn; {@code status} là {@code REQUESTED}, {@code REFUNDED} hoặc {@code FAILED}. */
	@Schema(name = "AdminUserRefund")
	public record RefundItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bookingId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(nullable = true) Long amountMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {
	}

	@Schema(name = "AdminUserEvent")
	public record EventItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(nullable = true) Instant startsAt) {
	}

	/** {@code events} chỉ có khi người dùng là organizer. Mỗi danh sách tối đa 50 dòng, mới nhất trước. */
	@Schema(name = "AdminUserDetail")
	public record UserDetail(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserItem profile,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BookingItem> bookings,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TicketItem> tickets,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RefundItem> refunds,
			@Schema(nullable = true) List<EventItem> events) {
	}

	@Schema(name = "AdminUserLockResult")
	public record LockResult(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled) {
	}

}
