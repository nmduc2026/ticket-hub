package com.ndz.notification.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.notification.service.NotificationService;

/** Nghe kết quả duyệt hồ sơ organizer của Catalog để gửi email (overview.md §7.6). */
@Component
class OrganizerReviewListener {

	private final NotificationService notifications;

	OrganizerReviewListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(OrganizerApproved event) {
		notifications.onOrganizerApproved(event);
	}

	@ApplicationModuleListener
	void on(OrganizerRejected event) {
		notifications.onOrganizerRejected(event);
	}

}
