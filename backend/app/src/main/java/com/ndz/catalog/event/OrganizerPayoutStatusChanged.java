package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * {@code charges_enabled} của connected account đổi giá trị (overview.md §7.6): Catalog cập nhật
 * {@code organizers.payout_ready}; {@code false} thì tạm dừng mọi sự kiện đang bán (BR-13). Do Payment phát; khai báo
 * ở module Catalog vì Catalog là bên nhận (Payment đã phụ thuộc Catalog qua {@code CatalogApi}).
 */
public record OrganizerPayoutStatusChanged(UUID messageId, Instant occurredAt, UUID organizerId, boolean payoutReady)
		implements IntegrationEvent {
}
