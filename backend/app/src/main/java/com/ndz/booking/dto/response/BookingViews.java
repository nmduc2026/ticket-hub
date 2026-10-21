package com.ndz.booking.dto.response;

import com.ndz.booking.entity.enums.BookingStatus;

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

	/** Dòng booking đang bị khoá để chuyển trạng thái. */
	public record LockedBooking(UUID id, UUID userId, UUID eventId, BookingStatus status, Instant expiresAt,
			Instant paymentStartedAt) {
	}

	public record BookingItemView(UUID seatId, String sectionCode, String rowLabel, int seatNumber, UUID tierId,
			String tierName, long priceMinor) {
	}

	public record BookingView(UUID id, UUID eventId, String status, long totalMinor, String currency,
			Instant expiresAt, List<BookingItemView> items) {
	}

}
