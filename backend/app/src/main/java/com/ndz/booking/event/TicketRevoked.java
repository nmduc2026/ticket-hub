package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Ticket đã thu hồi một vé (BR-09). Booking đếm các event này; khi đủ vé của booking mới nhả ghế về {@code AVAILABLE}
 * (ADR 0026). Khai báo ở module Booking vì Booking là bên nhận (xem {@link PaymentSucceeded}).
 */
public record TicketRevoked(UUID messageId, Instant occurredAt, UUID ticketId, UUID bookingId, UUID seatId)
		implements IntegrationEvent {
}
