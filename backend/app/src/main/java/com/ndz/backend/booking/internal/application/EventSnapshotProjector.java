package com.ndz.backend.booking.internal.application;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.backend.booking.internal.persistence.EventSnapshotRepository;
import com.ndz.backend.booking.internal.persistence.ProcessedEventRepository;
import com.ndz.backend.catalog.events.EventEnded;
import com.ndz.backend.catalog.events.EventOnSale;
import com.ndz.backend.catalog.events.EventUpdated;
import com.ndz.backend.shared.event.IntegrationEvent;

/**
 * Giữ {@code event_snapshots} theo event của Catalog sau khi publish (EventPublished do {@link PublishedEventHandler}
 * xử lý cùng với tồn kho) (overview.md §6.2): Booking kiểm giờ mở bán, trạng thái, giới hạn
 * vé từ bản sao này, không đọc DB của Catalog. Mỗi handler là idempotent: ghi {@code processed_events} cùng transaction;
 * event cũ hơn dữ liệu đang có bị bỏ qua.
 */
@Component
class EventSnapshotProjector {

	static final String CONSUMER = "booking.event-snapshots";

	private static final Logger log = LoggerFactory.getLogger(EventSnapshotProjector.class);

	private final ProcessedEventRepository processed;
	private final EventSnapshotRepository snapshots;

	EventSnapshotProjector(ProcessedEventRepository processed, EventSnapshotRepository snapshots) {
		this.processed = processed;
		this.snapshots = snapshots;
	}

	@ApplicationModuleListener
	void on(EventUpdated event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(), snapshots.updateDisplay(event), "EventUpdated");
	}

	@ApplicationModuleListener
	void on(EventOnSale event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(), snapshots.updateStatus(event.eventId(), "ON_SALE", event.occurredAt()),
				"EventOnSale");
	}

	@ApplicationModuleListener
	void on(EventEnded event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(), snapshots.updateStatus(event.eventId(), "ENDED", event.occurredAt()),
				"EventEnded");
	}

	private boolean alreadyProcessed(IntegrationEvent event) {
		boolean first = processed.markProcessed(CONSUMER, event.messageId());
		if (!first) {
			log.info("Bỏ qua event đã xử lý {} ({})", event.messageId(), event.getClass().getSimpleName());
		}
		return !first;
	}

	/**
	 * Không áp dụng được vì chưa có bản sao (event đến trước {@code EventPublished}) thì ném lỗi để transaction hoàn
	 * tác và Modulith gửi lại sau; không áp dụng được vì event cũ hơn dữ liệu hiện có thì bỏ qua.
	 */
	private void apply(UUID eventId, Instant occurredAt, boolean applied, String type) {
		if (applied) {
			return;
		}
		if (!snapshots.exists(eventId)) {
			throw new IllegalStateException(type + " đến trước EventPublished của sự kiện " + eventId);
		}
		log.info("Bỏ qua {} cũ hơn bản sao hiện có, sự kiện {} ({})", type, eventId, occurredAt);
	}

}
