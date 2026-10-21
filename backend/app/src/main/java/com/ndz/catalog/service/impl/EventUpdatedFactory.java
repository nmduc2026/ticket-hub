package com.ndz.catalog.service.impl;

import java.time.Clock;

import com.ndz.catalog.entity.Event;
import com.ndz.catalog.event.EventUpdated;
import com.ndz.shared.util.Uuid7;

/** Dựng {@link EventUpdated} từ trạng thái hiện tại của sự kiện, dùng chung cho sửa nội dung và đổi banner. */
final class EventUpdatedFactory {

	private EventUpdatedFactory() {
	}

	static EventUpdated of(Event event, Clock clock) {
		return new EventUpdated(Uuid7.next(clock), clock.instant(), event.getId(), event.getTitle(),
				event.getDescription(), event.getCategory(), event.getBannerUrl(), event.getDoorsOpenAt(),
				event.getAgeRestriction(), event.getRules());
	}

}
