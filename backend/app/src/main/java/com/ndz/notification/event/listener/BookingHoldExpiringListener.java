package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.notification.service.NotificationService;

/** Nghe {@code BookingHoldExpiring} do Booking phát để gửi email "sắp hết giờ giữ ghế" (nếu khách bật email). */
@Component
class BookingHoldExpiringListener {

	private final NotificationService notifications;

	BookingHoldExpiringListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(BookingHoldExpiring event) {
		notifications.onHoldExpiring(event);
	}

}
