package com.ndz.ticket.service.impl;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.ticket.repository.TicketInvariantRepository;
import com.ndz.ticket.service.TicketInvariantService;

@Service
public class TicketInvariantServiceImpl implements TicketInvariantService {

	private static final Logger log = LoggerFactory.getLogger(TicketInvariantServiceImpl.class);

	private final TicketInvariantRepository invariants;
	private final AtomicInteger duplicateSeats = new AtomicInteger();

	TicketInvariantServiceImpl(TicketInvariantRepository invariants, MeterRegistry metrics) {
		this.invariants = invariants;
		metrics.gauge("tickethub.invariant.duplicate_seat_tickets", duplicateSeats);
	}

	@Override
	@Transactional(readOnly = true)
	public int checkDuplicateSeatTickets() {
		int count = invariants.countSeatsWithMoreThanOneLiveTicket();
		duplicateSeats.set(count);
		if (count > 0) {
			log.error("{} ghế có hơn một vé chưa thu hồi: vi phạm bất biến BR-01", count);
		}
		return count;
	}

}
