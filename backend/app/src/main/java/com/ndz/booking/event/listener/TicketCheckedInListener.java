package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.TicketCheckedIn;
import com.ndz.booking.service.SalesTallyService;

/** Nghe {@code TicketCheckedIn} do Ticket phát để đếm số đã check-in theo hạng vé. */
@Component
class TicketCheckedInListener {

	private final SalesTallyService tally;

	TicketCheckedInListener(SalesTallyService tally) {
		this.tally = tally;
	}

	@ApplicationModuleListener
	void on(TicketCheckedIn event) {
		tally.onTicketCheckedIn(event);
	}

}
