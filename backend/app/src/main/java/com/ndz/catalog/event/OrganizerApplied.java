package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/** Một người dùng nộp hồ sơ làm organizer (overview.md §7.6); Notification báo cho Admin. */
public record OrganizerApplied(UUID messageId, Instant occurredAt, UUID organizerId, String displayName,
		String contactEmail) implements IntegrationEvent {
}
