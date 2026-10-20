package com.ndz.backend.catalog.events;

import java.time.Instant;
import java.util.UUID;

import com.ndz.backend.shared.events.IntegrationEvent;

/** Sự kiện đã kết thúc (qua giờ kết thúc): không bán thêm. */
public record EventEnded(UUID messageId, Instant occurredAt, UUID eventId) implements IntegrationEvent {
}
