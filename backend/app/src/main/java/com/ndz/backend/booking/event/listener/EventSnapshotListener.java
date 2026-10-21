package com.ndz.backend.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.backend.booking.service.EventProjectionService;
import com.ndz.backend.catalog.event.EventEnded;
import com.ndz.backend.catalog.event.EventOnSale;
import com.ndz.backend.catalog.event.EventUpdated;

/** Nghe các event còn lại của Catalog để giữ bản sao sự kiện; xử lý nằm ở {@link EventProjectionService}. */
@Component
class EventSnapshotListener {

	private final EventProjectionService projection;

	EventSnapshotListener(EventProjectionService projection) {
		this.projection = projection;
	}

	@ApplicationModuleListener
	void on(EventUpdated event) {
		projection.onUpdated(event);
	}

	@ApplicationModuleListener
	void on(EventOnSale event) {
		projection.onOnSale(event);
	}

	@ApplicationModuleListener
	void on(EventEnded event) {
		projection.onEnded(event);
	}

}
