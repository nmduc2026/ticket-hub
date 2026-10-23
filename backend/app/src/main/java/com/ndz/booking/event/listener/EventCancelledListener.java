package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.service.EventCancellationService;
import com.ndz.catalog.event.EventCancelled;

/** Nghe {@code EventCancelled} của Catalog; xử lý ở {@link EventCancellationService}. */
@Component
class EventCancelledListener {

	private final EventCancellationService cancellations;

	EventCancelledListener(EventCancellationService cancellations) {
		this.cancellations = cancellations;
	}

	@ApplicationModuleListener
	void on(EventCancelled event) {
		cancellations.onEventCancelled(event);
	}

}
