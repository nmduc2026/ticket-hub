package com.ndz.ticket.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingConfirmed;
import com.ndz.ticket.service.TicketIssuanceService;

/** Nghe {@code BookingConfirmed} do Booking phát để phát vé; xử lý nằm ở {@link TicketIssuanceService}. */
@Component
class BookingConfirmedListener {

	private final TicketIssuanceService issuance;

	BookingConfirmedListener(TicketIssuanceService issuance) {
		this.issuance = issuance;
	}

	@ApplicationModuleListener
	void on(BookingConfirmed event) {
		issuance.issueFor(event);
	}

}
