package com.ndz.backend.booking.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.backend.shared.event.IntegrationEvent;

/** Khách tự huỷ booking đang chờ thanh toán (BR-12): ghế đã được nhả ngay. */
public record BookingCancelled(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		List<UUID> seatIds) implements IntegrationEvent {
}
