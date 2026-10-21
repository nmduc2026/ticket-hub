package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Ticket đã check-in một vé (BR-09). Booking đếm vào {@code sales_by_event.checked_in_count}, tra hạng vé theo
 * {@code seatId} (overview.md §6.2). Khai báo ở module Booking vì Booking là bên nhận (xem {@link PaymentSucceeded}).
 */
public record TicketCheckedIn(UUID messageId, Instant occurredAt, UUID ticketId, UUID bookingId, UUID eventId,
		UUID seatId, String gate) implements IntegrationEvent {
}
