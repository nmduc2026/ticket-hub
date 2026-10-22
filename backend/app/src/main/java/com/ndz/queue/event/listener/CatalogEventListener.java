package com.ndz.queue.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.catalog.event.EventCancelled;
import com.ndz.catalog.event.EventEnded;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventSuspended;
import com.ndz.queue.service.QueueService;
import com.ndz.queue.service.QueueService.CloseReason;

/**
 * Nghe event của Catalog để giữ {@code queue:{eventId}:meta} và đóng phòng chờ (overview.md §7.1). Ghi / xoá key Redis
 * là idempotent nên không cần {@code processed_events}.
 */
@Component
class CatalogEventListener {

	private final QueueService queue;

	CatalogEventListener(QueueService queue) {
		this.queue = queue;
	}

	@ApplicationModuleListener
	void on(EventPublished event) {
		queue.registerEvent(event.eventId(), event.highDemand(), event.saleStartsAt());
	}

	@ApplicationModuleListener
	void on(EventSuspended event) {
		queue.close(event.eventId(), CloseReason.SUSPENDED);
	}

	@ApplicationModuleListener
	void on(EventCancelled event) {
		queue.close(event.eventId(), CloseReason.CANCELLED);
	}

	@ApplicationModuleListener
	void on(EventEnded event) {
		queue.close(event.eventId(), CloseReason.ENDED);
	}

}
