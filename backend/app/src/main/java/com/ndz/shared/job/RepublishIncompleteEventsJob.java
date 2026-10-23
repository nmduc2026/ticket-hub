package com.ndz.shared.job;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Gửi lại event giữa các module mà listener chưa xử lý xong (lỗi hạ tầng như DB mất kết nối; overview.md §7.3, §8.3).
 * Lỗi nghiệp vụ không đi qua đây: listener xử lý xong và phát event bù trừ. Listener đều idempotent nên gửi lại an toàn.
 * Chỉ gửi lại event đã quá {@code tickethub.events.republish-after} để không giẫm lên listener đang chạy. Kafka thay
 * cơ chế này bằng retry có backoff và DLT ở Sprint 8.
 */
@Component
class RepublishIncompleteEventsJob {

	private static final Logger log = LoggerFactory.getLogger(RepublishIncompleteEventsJob.class);

	private final IncompleteEventPublications incomplete;
	private final Duration republishAfter;

	RepublishIncompleteEventsJob(IncompleteEventPublications incomplete,
			@Value("${tickethub.events.republish-after:60s}") Duration republishAfter) {
		this.incomplete = incomplete;
		this.republishAfter = republishAfter;
	}

	@Scheduled(fixedDelayString = "${tickethub.events.republish-interval:30s}")
	@SchedulerLock(name = "republish-incomplete-events", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void run() {
		log.debug("Gửi lại event chưa xử lý xong quá {}", republishAfter);
		incomplete.resubmitIncompletePublicationsOlderThan(republishAfter);
	}

}
