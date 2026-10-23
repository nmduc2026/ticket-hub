package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Admin mở bán lại sự kiện đã tạm dừng (BR-13); {@code status} là {@code ON_SALE} hoặc {@code PUBLISHED}. */
public record EventResumed(UUID messageId, Instant occurredAt, UUID eventId, String status)
		implements IntegrationEvent {
}
