package com.ndz.booking.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Booking quá hạn giữ ghế (kể cả ân hạn khi đã bắt đầu thanh toán, BR-03): ghế đã được nhả. */
public record BookingExpired(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		List<UUID> seatIds) implements IntegrationEvent {
}
