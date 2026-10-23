package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.TicketIssuanceFailed;
import com.ndz.booking.service.BookingRefundService;

/** Nghe {@code TicketIssuanceFailed} do Ticket phát; xử lý nằm ở {@link BookingRefundService}. */
@Component
class TicketIssuanceFailedListener {

	private final BookingRefundService refunds;

	TicketIssuanceFailedListener(BookingRefundService refunds) {
		this.refunds = refunds;
	}

	@ApplicationModuleListener
	void on(TicketIssuanceFailed event) {
		refunds.onTicketIssuanceFailed(event);
	}

}
