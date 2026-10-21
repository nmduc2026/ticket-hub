package com.ndz.booking.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Một ghế trong đơn đặt vé và giá lúc đặt (overview.md §6.2). */
@Entity
@Table(schema = "booking", name = "booking_items")
public class BookingItem {

	@EmbeddedId
	private BookingItemId id;

	@Column(nullable = false, updatable = false)
	private UUID eventId;

	@Column(nullable = false, updatable = false)
	private UUID tierId;

	@Column(nullable = false, updatable = false)
	private long priceMinor;

	protected BookingItem() {
	}

	public BookingItem(UUID bookingId, UUID eventId, UUID seatId, UUID tierId, long priceMinor) {
		this.id = new BookingItemId(bookingId, seatId);
		this.eventId = eventId;
		this.tierId = tierId;
		this.priceMinor = priceMinor;
	}

	@Embeddable
	public record BookingItemId(UUID bookingId, UUID seatId) implements Serializable {
	}

}
