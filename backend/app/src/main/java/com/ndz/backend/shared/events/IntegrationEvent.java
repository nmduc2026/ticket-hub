package com.ndz.backend.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event giữa các module (overview.md §8.2). {@code messageId} là UUIDv7 của chính message, dùng để khử trùng ở
 * consumer ({@code processed_events}); khác với mã sự kiện (concert) nằm trong payload. Sau này map 1-1 sang
 * envelope Kafka ({@code eventId}, {@code occurredAt}).
 */
public interface IntegrationEvent {

	UUID messageId();

	Instant occurredAt();

}
