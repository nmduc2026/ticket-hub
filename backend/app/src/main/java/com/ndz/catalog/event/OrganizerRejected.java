package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Admin từ chối hồ sơ organizer kèm lý do (overview.md §7.6); Notification báo organizer (trong app và email). */
public record OrganizerRejected(UUID messageId, Instant occurredAt, UUID organizerId, String displayName,
		String contactEmail, String reason) implements IntegrationEvent {
}
