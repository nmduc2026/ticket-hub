package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.repository.EventSnapshotRepository;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.repository.SeatInventoryRepository;
import com.ndz.booking.service.EventProjectionService;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.api.CatalogApi.SellableSeat;
import com.ndz.catalog.event.EventEnded;
import com.ndz.catalog.event.EventOnSale;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventUpdated;
import com.ndz.catalog.event.SeatInventoryReady;
import com.ndz.shared.event.IntegrationEvent;
import com.ndz.shared.util.Uuid7;

@Service
public class EventProjectionServiceImpl implements EventProjectionService {

	static final String PUBLISHED_CONSUMER = "booking.event-published";
	static final String SNAPSHOT_CONSUMER = "booking.event-snapshots";

	private static final Logger log = LoggerFactory.getLogger(EventProjectionServiceImpl.class);

	private final ProcessedEventRepository processed;
	private final CatalogApi catalog;
	private final SeatInventoryRepository inventory;
	private final EventSnapshotRepository snapshots;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	EventProjectionServiceImpl(ProcessedEventRepository processed, CatalogApi catalog,
			SeatInventoryRepository inventory, EventSnapshotRepository snapshots, ApplicationEventPublisher publisher,
			Clock clock) {
		this.processed = processed;
		this.catalog = catalog;
		this.inventory = inventory;
		this.snapshots = snapshots;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public void onPublished(EventPublished event) {
		if (!processed.markProcessed(PUBLISHED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua EventPublished đã xử lý {}", event.messageId());
			return;
		}
		if (!snapshots.upsertPublished(event)) {
			log.info("EventPublished cũ hơn bản sao hiện có, giữ nguyên bản sao, sự kiện {}", event.eventId());
		}
		List<SellableSeat> seats = catalog.sellableSeats(event.eventId());
		int added = inventory.insertAll(event.eventId(), seats);
		log.info("Tồn kho sự kiện {}: {} ghế đang bán, thêm mới {}", event.eventId(), seats.size(), added);
		publisher.publishEvent(new SeatInventoryReady(Uuid7.next(clock), clock.instant(), event.eventId(),
				inventory.count(event.eventId())));
	}

	@Override
	public void onUpdated(EventUpdated event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(), snapshots.updateDisplay(event), "EventUpdated");
	}

	@Override
	public void onOnSale(EventOnSale event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(),
				snapshots.updateStatus(event.eventId(), "ON_SALE", event.occurredAt()), "EventOnSale");
	}

	@Override
	public void onEnded(EventEnded event) {
		if (alreadyProcessed(event)) {
			return;
		}
		apply(event.eventId(), event.occurredAt(),
				snapshots.updateStatus(event.eventId(), "ENDED", event.occurredAt()), "EventEnded");
	}

	private boolean alreadyProcessed(IntegrationEvent event) {
		boolean first = processed.markProcessed(SNAPSHOT_CONSUMER, event.messageId());
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
