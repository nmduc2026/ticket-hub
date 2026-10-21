package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.notification.service.InAppNotificationService;
import com.ndz.ticket.event.TicketIssued;

/**
 * Tạo thông báo trong app từ event nghiệp vụ, độc lập với consumer gửi email (overview.md §6.5): email lỗi không làm
 * mất thông báo và ngược lại. Idempotent theo {@code (user_id, source_event_id)}.
 */
@Component
class InAppNotificationListener {

	private final InAppNotificationService notifications;

	InAppNotificationListener(InAppNotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(TicketIssued event) {
		notifications.onTicketIssued(event);
	}

	@ApplicationModuleListener
	void on(RefundRequested event) {
		notifications.onRefundRequested(event);
	}

	@ApplicationModuleListener
	void on(RefundCompleted event) {
		notifications.onRefundCompleted(event);
	}

	@ApplicationModuleListener
	void on(RefundFailed event) {
		notifications.onRefundFailed(event);
	}

}
