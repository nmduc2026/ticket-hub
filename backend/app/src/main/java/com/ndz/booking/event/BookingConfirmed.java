package com.ndz.booking.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Booking đã xác nhận sau khi thanh toán thành công: ghế đã bán. Mang sẵn tên sự kiện, giờ diễn, địa điểm, ghế và
 * email liên hệ để Ticket in lên vé và Notification gửi email mà không cần bản sao riêng (overview.md §6.2, §7.2).
 */
public record BookingConfirmed(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String eventTitle, Instant startsAt, String venueName, String contactEmail, long totalMinor, String currency,
		List<Seat> seats) implements IntegrationEvent {

	/** Một ghế của booking; giá là số nguyên đơn vị nhỏ nhất (BR-10). */
	public record Seat(UUID seatId, String sectionCode, String rowLabel, int seatNumber, UUID tierId, String tierName,
			long priceMinor) {
	}

}
