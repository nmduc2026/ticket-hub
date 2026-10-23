package com.ndz.catalog.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.catalog.event.OrganizerPayoutStatusChanged;
import com.ndz.catalog.service.EventSuspensionService;

/** Nghe {@code OrganizerPayoutStatusChanged} do Payment phát; xử lý ở {@link EventSuspensionService}. */
@Component
class OrganizerPayoutStatusListener {

	private final EventSuspensionService suspensions;

	OrganizerPayoutStatusListener(EventSuspensionService suspensions) {
		this.suspensions = suspensions;
	}

	@ApplicationModuleListener
	void on(OrganizerPayoutStatusChanged event) {
		suspensions.onPayoutStatusChanged(event);
	}

}
