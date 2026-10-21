package com.ndz.backend.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.backend.shared.event.IntegrationEvent;

/** Sự kiện đã kết thúc (qua giờ kết thúc): không bán thêm. */
public record EventEnded(UUID messageId, Instant occurredAt, UUID eventId) implements IntegrationEvent {
}
