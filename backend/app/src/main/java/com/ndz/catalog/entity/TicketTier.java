package com.ndz.catalog.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Hạng vé của một sự kiện; giá là số nguyên đơn vị nhỏ nhất (BR-10), VND không nhân 100. */
@Entity
@Table(schema = "catalog", name = "ticket_tiers")
public class TicketTier {

	public static final String CURRENCY = "VND";

	@Id
	private UUID id;

	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	private String name;

	@Column(name = "price_minor")
	private long priceMinor;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3)
	private String currency;

	protected TicketTier() {
	}

	public TicketTier(UUID id, UUID eventId, String name, long priceMinor) {
		this.id = id;
		this.eventId = eventId;
		this.currency = CURRENCY;
		change(name, priceMinor);
	}

	public void change(String name, long priceMinor) {
		this.name = name;
		this.priceMinor = priceMinor;
	}

	public UUID getId() {
		return id;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getName() {
		return name;
	}

	public long getPriceMinor() {
		return priceMinor;
	}

	public String getCurrency() {
		return currency;
	}

}
