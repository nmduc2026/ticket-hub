package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundRequested;
import com.ndz.notification.service.NotificationService;

/** Nghe {@code RefundRequested} để gửi email bắt buộc khi sự kiện bị huỷ (BR-08). */
@Component
class EventCancelledEmailListener {

	private final NotificationService notifications;

	EventCancelledEmailListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(RefundRequested event) {
		notifications.onRefundRequested(event);
	}

}
