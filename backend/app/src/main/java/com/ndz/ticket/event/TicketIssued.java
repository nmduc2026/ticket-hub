package com.ndz.ticket.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Vé của một booking đã phát, một lần cho cả booking. Mang đủ thông tin để Notification gửi email mà không cần bản
 * sao riêng (overview.md §6.5); không mang mã QR (người dùng lấy vé ở "Vé của tôi").
 */
public record TicketIssued(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String contactEmail, String eventTitle, Instant startsAt, String venueName, List<Ticket> tickets)
		implements IntegrationEvent {

	/** Một vé đã phát. */
	public record Ticket(UUID ticketId, String sectionCode, String rowLabel, int seatNumber, String tierName) {
	}

}
