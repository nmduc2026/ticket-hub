package com.ndz.ticket.mapper;

import org.springframework.stereotype.Component;

import com.ndz.catalog.api.CatalogApi.GuestInfo;
import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;
import com.ndz.ticket.dto.response.TicketResponses.TicketSeat;
import com.ndz.ticket.entity.Ticket;

@Component
public class TicketMapper {

	/** {@code guest} là thông tin cho khách hiện tại của sự kiện; null nếu không lấy được. */
	public TicketResponse toResponse(Ticket ticket, GuestInfo guest) {
		return new TicketResponse(ticket.getId(), ticket.getEventId(), ticket.getEventTitle(), ticket.getStartsAt(),
				ticket.getVenueName(), new TicketSeat(ticket.getSectionCode(), ticket.getRowLabel(),
						ticket.getSeatNumber()),
				ticket.getTierName(), ticket.getStatus(), ticket.getQrToken(), guest == null ? null : guest.doorsOpenAt(),
				guest == null ? null : guest.ageRestriction(), guest == null ? null : guest.rules());
	}

}
