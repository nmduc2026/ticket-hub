package com.ndz.queue.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.ndz.queue.service.QueueAdmissionService;

/**
 * Mỗi giây gọi người từ phòng chờ vào chọn ghế (overview.md §7.1). ShedLock để nhiều pod chỉ có một nơi điều tiết;
 * Lua script nguyên tử nên nếu hai pod lỡ cùng chạy thì cũng không gọi vượt {@code max_active}.
 */
@Component
class AdmitWaitingJob {

	private final QueueAdmissionService admission;

	AdmitWaitingJob(QueueAdmissionService admission) {
		this.admission = admission;
	}

	@Scheduled(fixedDelayString = "${tickethub.queue.admit-interval:1s}")
	@SchedulerLock(name = "queue-admit-waiting", lockAtMostFor = "PT10S", lockAtLeastFor = "PT0.5S")
	void run() {
		admission.admitWaiting();
	}

}
