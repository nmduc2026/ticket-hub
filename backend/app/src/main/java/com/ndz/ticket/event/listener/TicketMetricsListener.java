package com.ndz.ticket.event.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.TicketIssuanceFailed;
import com.ndz.ticket.event.TicketIssued;

/**
 * Metric nghiệp vụ của Ticket (overview.md §16.1, §16.3). {@code tickethub.tickets.issuance_failed} tăng nghĩa là lớp 2
 * chống oversell đã để lọt: alert bất biến mức cao nhất.
 */
@Component
class TicketMetricsListener {

	private final Counter issued;
	private final Counter issuanceFailed;

	TicketMetricsListener(MeterRegistry metrics) {
		this.issued = Counter.builder("tickethub.tickets.issued").description("Số vé đã phát").register(metrics);
		this.issuanceFailed = Counter.builder("tickethub.tickets.issuance_failed")
				.description("Booking không phát được vé vì ghế đã có vé (vi phạm bất biến)").register(metrics);
	}

	@EventListener
	void on(TicketIssued event) {
		issued.increment(event.tickets().size());
	}

	@EventListener
	void on(TicketIssuanceFailed event) {
		issuanceFailed.increment();
	}

}
