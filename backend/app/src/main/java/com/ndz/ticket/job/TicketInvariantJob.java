package com.ndz.ticket.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.ndz.ticket.service.TicketInvariantService;

/** Chạy truy vấn ghế trùng định kỳ (overview.md §10, §16.3); ShedLock để nhiều instance chỉ chạy một nơi. */
@Component
class TicketInvariantJob {

	private final TicketInvariantService invariants;

	TicketInvariantJob(TicketInvariantService invariants) {
		this.invariants = invariants;
	}

	@Scheduled(fixedDelayString = "${tickethub.ticket.invariant-interval:60s}")
	@SchedulerLock(name = "ticket-invariant-check", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void run() {
		invariants.checkDuplicateSeatTickets();
	}

}
