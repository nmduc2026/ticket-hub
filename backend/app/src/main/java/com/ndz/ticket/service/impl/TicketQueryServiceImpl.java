package com.ndz.ticket.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.api.CatalogApi.GuestInfo;
import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;
import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.mapper.TicketMapper;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.TicketQueryService;

@Service
public class TicketQueryServiceImpl implements TicketQueryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TicketRepository tickets;
	private final TicketMapper mapper;
	private final CatalogApi catalog;

	TicketQueryServiceImpl(TicketRepository tickets, TicketMapper mapper, CatalogApi catalog) {
		this.tickets = tickets;
		this.mapper = mapper;
		this.catalog = catalog;
	}

	@Override
	@Transactional(readOnly = true)
	public List<TicketResponse> myTickets(UUID userId, int page, int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		List<Ticket> found = tickets.findByOwnerUserIdOrderByIssuedAtDescIdAsc(userId, request);
		// Giờ mở cửa, độ tuổi, quy định sửa được sau publish (BR-14) nên đọc bản hiện tại thay vì chụp vào vé.
		Map<UUID, GuestInfo> guest = catalog.guestInfo(found.stream().map(Ticket::getEventId).distinct().toList());
		return found.stream().map(ticket -> mapper.toResponse(ticket, guest.get(ticket.getEventId()))).toList();
	}

}
