package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Sự kiện chuyển sang đang bán: tới giờ mở bán và tồn kho ghế đã sẵn sàng (overview.md §7.7, §7.9). */
public record EventOnSale(UUID messageId, Instant occurredAt, UUID eventId) implements IntegrationEvent {
}
