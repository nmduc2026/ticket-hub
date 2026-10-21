package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Sự kiện bị tạm dừng bán (BR-13): Booking cập nhật bản sao, từ chối giữ ghế mới và báo từng khách có đơn; Notification
 * báo organizer, và báo Admin khi tạm dừng tự động ({@code automatic}: organizer bị đình chỉ hoặc mất
 * {@code charges_enabled}). Vé đã bán giữ nguyên.
 */
public record EventSuspended(UUID messageId, Instant occurredAt, UUID eventId, UUID organizerId, String eventTitle,
		String reason, boolean automatic) implements IntegrationEvent {
}
