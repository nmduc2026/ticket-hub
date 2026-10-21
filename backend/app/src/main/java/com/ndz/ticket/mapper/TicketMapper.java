package com.ndz.ticket.mapper;

import org.springframework.stereotype.Component;

import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;
import com.ndz.ticket.dto.response.TicketResponses.TicketSeat;
import com.ndz.ticket.entity.Ticket;

@Component
public class TicketMapper {

	public TicketResponse toResponse(Ticket ticket) {
		return new TicketResponse(ticket.getId(), ticket.getEventTitle(), ticket.getStartsAt(), ticket.getVenueName(),
				new TicketSeat(ticket.getSectionCode(), ticket.getRowLabel(), ticket.getSeatNumber()),
				ticket.getTierName(), ticket.getStatus(), ticket.getQrToken());
	}

}
