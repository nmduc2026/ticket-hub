package com.ndz.backend.booking.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.backend.shared.event.IntegrationEvent;

/** Khách giữ ghế thành công; booking ở trạng thái chờ thanh toán đến {@code expiresAt} (BR-03). */
public record BookingCreated(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		List<UUID> seatIds, long totalMinor, String currency, Instant expiresAt, String contactEmail)
		implements IntegrationEvent {
}
