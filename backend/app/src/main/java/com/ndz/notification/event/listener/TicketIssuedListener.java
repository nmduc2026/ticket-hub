package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.notification.service.NotificationService;
import com.ndz.ticket.event.TicketIssued;

/** Nghe {@code TicketIssued} do Ticket phát để gửi email vé; xử lý nằm ở {@link NotificationService}. */
@Component
class TicketIssuedListener {

	private final NotificationService notifications;

	TicketIssuedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(TicketIssued event) {
		notifications.onTicketIssued(event);
	}

}
