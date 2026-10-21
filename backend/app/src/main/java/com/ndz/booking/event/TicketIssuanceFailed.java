package com.ndz.booking.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Ticket không phát được vé cho booking {@code CONFIRMED} vì ghế đã có vé chưa thu hồi (vi phạm bất biến BR-01; lớp 2
 * chống oversell đã để lọt, overview.md §7.3). Lỗi nghiệp vụ nên không thử lại: Booking chuyển {@code REFUND_REQUESTED}
 * và Payment hoàn 100%. Khai báo ở module Booking vì Booking là bên nhận (xem {@link PaymentSucceeded}).
 */
public record TicketIssuanceFailed(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String eventTitle, String contactEmail, List<UUID> conflictingSeatIds, long totalMinor, String currency)
		implements IntegrationEvent {
}
