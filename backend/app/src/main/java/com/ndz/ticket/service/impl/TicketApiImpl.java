package com.ndz.ticket.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ndz.ticket.api.TicketApi;
import com.ndz.ticket.service.TicketQueryService;

/** Cài đặt {@link TicketApi} trong cùng process (giai đoạn modular monolith). */
@Service
class TicketApiImpl implements TicketApi {

	private final TicketQueryService queries;

	TicketApiImpl(TicketQueryService queries) {
		this.queries = queries;
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
