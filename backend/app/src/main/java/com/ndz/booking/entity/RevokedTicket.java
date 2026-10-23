package com.ndz.booking.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Một vé của booking đã được Ticket thu hồi; đủ số dòng bằng số ghế thì nhả ghế (overview.md §7.4). */
@Entity
@Table(schema = "booking", name = "revoked_tickets")
public class RevokedTicket {

	@EmbeddedId
	private RevokedTicketId id;

	private Instant revokedAt;

	protected RevokedTicket() {
	}

	@Embeddable
	public record RevokedTicketId(UUID bookingId, UUID ticketId) implements Serializable {
	}

}
