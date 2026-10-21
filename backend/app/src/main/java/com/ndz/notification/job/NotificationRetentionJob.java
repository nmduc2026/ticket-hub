package com.ndz.notification.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.ndz.notification.service.InAppNotificationService;

/** Xoá thông báo trong app cũ hơn thời hạn giữ (overview.md §6.5: 90 ngày); mỗi ngày một lần, ShedLock. */
@Component
class NotificationRetentionJob {

	private final InAppNotificationService notifications;

	NotificationRetentionJob(InAppNotificationService notifications) {
		this.notifications = notifications;
	}

	@Scheduled(cron = "${tickethub.notification.retention-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
	@SchedulerLock(name = "purge-notifications", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	void run() {
		notifications.purgeExpired();
	}

}
