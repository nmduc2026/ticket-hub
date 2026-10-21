package com.ndz.catalog.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Gán một khu ghế cho một hạng vé trong một sự kiện. Khoá chính (sự kiện, khu) làm DB chặn một khu thuộc hai hạng vé;
 * FK ghép (sự kiện, hạng vé) chặn gán hạng vé của sự kiện khác (overview.md §6.1).
 */
@Entity
@Table(schema = "catalog", name = "tier_sections")
public class TierSection {

	@EmbeddedId
	private Key id;

	@Column(name = "tier_id", nullable = false)
	private UUID tierId;

	protected TierSection() {
	}

	public TierSection(UUID eventId, UUID sectionId, UUID tierId) {
		this.id = new Key(eventId, sectionId);
		this.tierId = tierId;
	}

	public UUID getEventId() {
		return id.eventId;
	}

	public UUID getSectionId() {
		return id.sectionId;
	}

	public UUID getTierId() {
		return tierId;
	}

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "event_id")
		private UUID eventId;

		@Column(name = "section_id")
		private UUID sectionId;

		protected Key() {
		}

		Key(UUID eventId, UUID sectionId) {
			this.eventId = eventId;
			this.sectionId = sectionId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && eventId.equals(key.eventId) && sectionId.equals(key.sectionId);
		}

		@Override
		public int hashCode() {
			return 31 * eventId.hashCode() + sectionId.hashCode();
		}

	}

}
