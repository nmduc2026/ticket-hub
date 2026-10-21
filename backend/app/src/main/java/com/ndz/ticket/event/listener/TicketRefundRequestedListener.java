package com.ndz.ticket.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundRequested;
import com.ndz.ticket.service.TicketRevocationService;

/** Nghe {@code RefundRequested} do Booking phát để thu hồi vé; xử lý nằm ở {@link TicketRevocationService}. */
@Component
class TicketRefundRequestedListener {

	private final TicketRevocationService revocation;

	TicketRefundRequestedListener(TicketRevocationService revocation) {
		this.revocation = revocation;
	}

	@ApplicationModuleListener
	void on(RefundRequested event) {
		revocation.revokeFor(event);
	}

}
