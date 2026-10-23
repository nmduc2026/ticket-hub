package com.ndz.account.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Message đã xử lý của một consumer (overview.md §8.3); ghi bằng {@code INSERT ... ON CONFLICT DO NOTHING}. */
@Entity
@Table(schema = "account", name = "processed_events")
public class AccountProcessedEvent {

	@EmbeddedId
	private AccountProcessedEventId id;

	private Instant processedAt;

	protected AccountProcessedEvent() {
	}

	@Embeddable
	public record AccountProcessedEventId(String consumer, UUID messageId) implements Serializable {
	}

}
