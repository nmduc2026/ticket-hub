package com.ndz.backend.booking.internal.application;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.backend.booking.internal.persistence.EventSnapshotRepository;
import com.ndz.backend.booking.internal.persistence.ProcessedEventRepository;
import com.ndz.backend.booking.internal.persistence.SeatInventoryRepository;
import com.ndz.backend.catalog.CatalogApi;
import com.ndz.backend.catalog.CatalogApi.SellableSeat;
import com.ndz.backend.catalog.events.EventPublished;
import com.ndz.backend.catalog.events.SeatInventoryReady;
import com.ndz.backend.shared.util.Uuid7;

/**
 * Xử lý {@code EventPublished} (overview.md §7.9, ADR 0021) trong một transaction, theo thứ tự: dựng
 * {@code event_snapshots}, lấy danh sách ghế đang bán qua interface công khai của Catalog, ghi {@code seat_inventory}
 * idempotent, rồi báo {@code SeatInventoryReady}. Gộp một handler vì tồn kho tham chiếu bản sao sự kiện, hai handler
 * riêng sẽ chạy song song và có thể chạy sai thứ tự. Nhận event lần hai không tạo thêm gì và không báo lại.
 */
@Component
class PublishedEventHandler {

	static final String CONSUMER = "booking.event-published";

	private static final Logger log = LoggerFactory.getLogger(PublishedEventHandler.class);

	private final ProcessedEventRepository processed;
	private final CatalogApi catalog;
	private final SeatInventoryRepository inventory;
	private final EventSnapshotRepository snapshots;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	PublishedEventHandler(ProcessedEventRepository processed, CatalogApi catalog, SeatInventoryRepository inventory,
			EventSnapshotRepository snapshots, ApplicationEventPublisher publisher, Clock clock) {
		this.processed = processed;
		this.catalog = catalog;
		this.inventory = inventory;
		this.snapshots = snapshots;
		this.publisher = publisher;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(EventPublished event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
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

}
