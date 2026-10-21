package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.TicketRevoked;
import com.ndz.booking.service.BookingRefundService;

/** Nghe {@code TicketRevoked} do Ticket phát; xử lý nằm ở {@link BookingRefundService}. */
@Component
class TicketRevokedListener {

	private final BookingRefundService refunds;

	TicketRevokedListener(BookingRefundService refunds) {
		this.refunds = refunds;
	}

	@ApplicationModuleListener
	void on(TicketRevoked event) {
		refunds.onTicketRevoked(event);
	}

}
