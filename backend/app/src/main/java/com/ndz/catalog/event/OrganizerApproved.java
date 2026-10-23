package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Admin duyệt hồ sơ organizer (overview.md §7.6); Account gán role {@code ORGANIZER}, Notification báo organizer. */
public record OrganizerApproved(UUID messageId, Instant occurredAt, UUID organizerId, String displayName,
		String contactEmail) implements IntegrationEvent {
}
