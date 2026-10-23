package com.ndz.ticket.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.api.BookingApi;
import com.ndz.booking.api.BookingApi.BookingContact;
import com.ndz.ticket.dto.response.ResendResponses.BulkItem;
import com.ndz.ticket.dto.response.ResendResponses.BulkResult;
import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.entity.enums.TicketStatus;
import com.ndz.ticket.event.TicketIssued;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.OrganizerTicketResendService;
import com.ndz.shared.util.Uuid7;

@Service
class OrganizerTicketResendServiceImpl implements OrganizerTicketResendService {

	private static final Logger log = LoggerFactory.getLogger(OrganizerTicketResendServiceImpl.class);

	private final BookingApi bookings;
	private final TicketRepository tickets;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	OrganizerTicketResendServiceImpl(BookingApi bookings, TicketRepository tickets,
			ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
		this.tickets = tickets;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	@Transactional
	public BulkResult resend(UUID organizerId, List<UUID> bookingIds) {
		return new BulkResult(bookingIds.stream().map(id -> resendOne(organizerId, id)).toList());
	}

	private BulkItem resendOne(UUID organizerId, UUID bookingId) {
		BookingContact contact = bookings.contactOf(bookingId).orElse(null);
		if (contact == null || !organizerId.equals(contact.organizerId())) {
			return new BulkItem(bookingId, false, "NOT_EVENT_OWNER");
		}
		if (!"CONFIRMED".equals(contact.status())) {
			return new BulkItem(bookingId, false, "BOOKING_NOT_CONFIRMED");
		}
		List<Ticket> valid = tickets.findByBookingId(bookingId).stream()
				.filter(ticket -> ticket.getStatus() != TicketStatus.REVOKED).toList();
		if (valid.isEmpty()) {
			return new BulkItem(bookingId, false, "TICKET_REVOKED");
		}
		publisher.publishEvent(new TicketIssued(Uuid7.next(clock), clock.instant(), bookingId, valid.get(0).getOwnerUserId(),
				valid.get(0).getEventId(), contact.contactEmail(), contact.eventTitle(), contact.startsAt(),
				contact.venueName(),
				valid.stream().map(ticket -> new TicketIssued.Ticket(ticket.getId(), ticket.getSectionCode(),
						ticket.getRowLabel(), ticket.getSeatNumber(), ticket.getTierName())).toList()));
		log.info("Gửi lại email vé cho booking {} ({} vé)", bookingId, valid.size());
		return new BulkItem(bookingId, true, null);
	}

}
