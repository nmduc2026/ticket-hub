package com.ndz.backend.catalog.internal.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.backend.catalog.events.SeatInventoryReady;

/** Booking đã tạo xong tồn kho ghế → sự kiện đủ điều kiện chuyển ON_SALE khi tới giờ mở bán (overview.md §7.9). */
@Component
class SeatInventoryReadyListener {

	private final EventLifecycleService lifecycle;

	SeatInventoryReadyListener(EventLifecycleService lifecycle) {
		this.lifecycle = lifecycle;
	}

	@ApplicationModuleListener
	void on(SeatInventoryReady event) {
		lifecycle.markInventoryReady(event.eventId());
	}

}
