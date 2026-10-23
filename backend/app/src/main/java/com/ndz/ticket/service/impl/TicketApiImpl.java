package com.ndz.ticket.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.ticket.api.TicketApi;
import com.ndz.ticket.entity.enums.TicketStatus;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.TicketQueryService;

/** Cài đặt {@link TicketApi} trong cùng process (giai đoạn modular monolith). */
@Service
class TicketApiImpl implements TicketApi {

	private final TicketQueryService queries;
	private final TicketRepository tickets;

	TicketApiImpl(TicketQueryService queries, TicketRepository tickets) {
		this.queries = queries;
		this.tickets = tickets;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasUpcomingTickets(UUID userId, Instant now) {
		return tickets.existsByOwnerUserIdAndStatusAndStartsAtAfter(userId, TicketStatus.VALID, now);
	}

	@Override
	public List<UserTicket> ticketsOf(UUID userId, int limit) {
		return queries.myTickets(userId, 0, limit).stream()
				.map(ticket -> new UserTicket(ticket.ticketId(), ticket.eventTitle(), ticket.startsAt(),
						ticket.venueName(), ticket.seat().sectionCode(), ticket.seat().row(), ticket.seat().number(),
						ticket.tierName(), ticket.status().name()))
				.toList();
	}

}
