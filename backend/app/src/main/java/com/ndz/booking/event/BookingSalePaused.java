package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Sự kiện của booking này bị tạm dừng bán (BR-13), phát cho từng booking {@code PENDING_PAYMENT} / {@code CONFIRMED}
 * để Notification báo khách mà không hỏi ngược Booking (overview.md §6.5). Vé đã mua giữ nguyên.
 */
public record BookingSalePaused(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String eventTitle, String contactEmail, String bookingStatus) implements IntegrationEvent {
}
