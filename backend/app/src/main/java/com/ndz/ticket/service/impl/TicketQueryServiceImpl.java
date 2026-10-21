package com.ndz.ticket.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;
import com.ndz.ticket.mapper.TicketMapper;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.TicketQueryService;

@Service
public class TicketQueryServiceImpl implements TicketQueryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TicketRepository tickets;
	private final TicketMapper mapper;

	TicketQueryServiceImpl(TicketRepository tickets, TicketMapper mapper) {
		this.tickets = tickets;
		this.mapper = mapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<TicketResponse> myTickets(UUID userId, int page, int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		return tickets.findByOwnerUserIdOrderByIssuedAtDescIdAsc(userId, request).stream().map(mapper::toResponse)
				.toList();
	}

}
