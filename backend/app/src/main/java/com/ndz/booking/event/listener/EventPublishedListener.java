package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.service.EventProjectionService;
import com.ndz.catalog.event.EventPublished;

/** Nghe {@code EventPublished} của Catalog (overview.md §7.9); xử lý nằm ở {@link EventProjectionService}. */
@Component
class EventPublishedListener {

	private final EventProjectionService projection;

	EventPublishedListener(EventProjectionService projection) {
		this.projection = projection;
	}

	@ApplicationModuleListener
	void on(EventPublished event) {
		projection.onPublished(event);
	}

}
