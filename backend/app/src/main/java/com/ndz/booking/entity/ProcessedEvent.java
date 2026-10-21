package com.ndz.booking.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Message đã xử lý của một consumer (overview.md §8.3); ghi bằng {@code INSERT ... ON CONFLICT DO NOTHING}. */
@Entity
@Table(schema = "booking", name = "processed_events")
public class ProcessedEvent {

	@EmbeddedId
	private ProcessedEventId id;

	private Instant processedAt;

	protected ProcessedEvent() {
	}

	@Embeddable
	public record ProcessedEventId(String consumer, UUID messageId) implements Serializable {
	}

}
