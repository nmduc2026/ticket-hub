package com.ndz.backend.booking.internal.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Giá trị của đơn đặt vé dùng giữa các lớp của module booking. */
public final class BookingViews {

	private BookingViews() {
	}

	/** Thông tin sự kiện Booking cần để kiểm quy tắc đặt vé (từ {@code event_snapshots}). */
	public record EventRules(UUID eventId, String status, int maxTicketsPerUser, Instant saleStartsAt) {
	}

	/** Một ghế đã khoá trong tồn kho. */
	public record LockedSeat(UUID seatId, UUID tierId, String tierName, String sectionCode, String rowLabel,
			int seatNumber, long priceMinor, String currency, String status) {
	}

	public record BookingItemView(UUID seatId, String sectionCode, String rowLabel, int seatNumber, UUID tierId,
			String tierName, long priceMinor) {
	}

	public record BookingView(UUID id, UUID eventId, String status, long totalMinor, String currency,
			Instant expiresAt, List<BookingItemView> items) {
	}

}
