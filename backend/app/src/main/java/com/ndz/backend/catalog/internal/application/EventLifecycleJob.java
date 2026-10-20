package com.ndz.backend.catalog.internal.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job định kỳ: chuyển sự kiện sang đang bán / kết thúc theo giờ. ShedLock bảo đảm một instance chạy tại một lúc. */
@Component
class EventLifecycleJob {

	private static final Logger log = LoggerFactory.getLogger(EventLifecycleJob.class);

	private final EventLifecycleService lifecycle;

	EventLifecycleJob(EventLifecycleService lifecycle) {
		this.lifecycle = lifecycle;
	}

	@Scheduled(fixedDelayString = "${tickethub.catalog.lifecycle-interval:30s}")
	@SchedulerLock(name = "event-lifecycle", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void run() {
		int changed = lifecycle.advance();
		if (changed > 0) {
			log.info("Vòng đời sự kiện: {} sự kiện đổi trạng thái", changed);
		}
	}

}
