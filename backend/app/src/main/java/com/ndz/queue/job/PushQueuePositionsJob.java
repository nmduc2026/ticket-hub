package com.ndz.queue.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.queue.service.QueueStreamService;

/**
 * Đẩy vị trí mới cho khách đang nghe SSE trên pod này. Mỗi pod đẩy cho kết nối của chính nó nên không cần ShedLock.
 */
@Component
class PushQueuePositionsJob {

	private final QueueStreamService streams;

	PushQueuePositionsJob(QueueStreamService streams) {
		this.streams = streams;
	}

	@Scheduled(fixedDelayString = "${tickethub.queue.position-interval:5s}")
	void run() {
		streams.pushPositions();
	}

}
