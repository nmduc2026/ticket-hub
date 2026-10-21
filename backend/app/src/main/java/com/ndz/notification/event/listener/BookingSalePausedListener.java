package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingSalePaused;
import com.ndz.notification.service.NotificationService;

/** Nghe {@code BookingSalePaused} do Booking phát để gửi email "tạm dừng bán" (nếu khách bật email). */
@Component
class BookingSalePausedListener {

	private final NotificationService notifications;

	BookingSalePausedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(BookingSalePaused event) {
		notifications.onSalePaused(event);
	}

}
