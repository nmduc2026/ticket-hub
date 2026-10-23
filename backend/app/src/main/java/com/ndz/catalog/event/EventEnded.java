package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Sự kiện đã kết thúc (qua giờ kết thúc): không bán thêm. */
public record EventEnded(UUID messageId, Instant occurredAt, UUID eventId) implements IntegrationEvent {
}
