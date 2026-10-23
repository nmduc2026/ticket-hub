package com.ndz.shared.idempotency;

import java.time.Clock;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dọn bản ghi quá hạn (giữ 24 giờ). Chạy trong một câu lệnh, chạy lại nhiều lần không gây sai. */
@Component
class IdempotencyCleaner {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleaner.class);

	private final IdempotencyStore store;
	private final Clock clock;

	IdempotencyCleaner(IdempotencyStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	@Scheduled(cron = "${tickethub.idempotency.cleanup-cron:0 */10 * * * *}")
	@SchedulerLock(name = "idempotency-cleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
	void cleanUp() {
		int deleted = deleteExpired();
		if (deleted > 0) {
			log.info("Đã dọn {} Idempotency-Key hết hạn", deleted);
		}
	}

	int deleteExpired() {
		return store.deleteExpired(clock.instant());
	}

}
