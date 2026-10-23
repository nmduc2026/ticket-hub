package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundCompleted;
import com.ndz.notification.service.NotificationService;

/** Nghe {@code RefundCompleted} do Payment phát để gửi email xác nhận hoàn tiền. */
@Component
class RefundCompletedListener {

	private final NotificationService notifications;

	RefundCompletedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(RefundCompleted event) {
		notifications.onRefundCompleted(event);
	}

}
