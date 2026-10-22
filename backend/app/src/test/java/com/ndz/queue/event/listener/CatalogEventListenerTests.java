package com.ndz.queue.event.listener;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

import com.ndz.catalog.event.EventCancelled;
import com.ndz.catalog.event.EventEnded;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventSuspended;
import com.ndz.queue.service.QueueService;
import com.ndz.shared.util.Uuid7;

/** Listener chỉ chuyển event của Catalog sang QueueService (plan Sprint 7 hạng mục 1; overview.md §7.1). */
class CatalogEventListenerTests {

	private static final Instant NOW = Instant.parse("2026-12-01T03:00:00Z");

	private final QueueService queue = Mockito.mock(QueueService.class);
	private final CatalogEventListener listener = new CatalogEventListener(queue);
	private final UUID eventId = UUID.randomUUID();

	@Test
	@DisplayName("EventPublished ghi lại high_demand và sale_starts_at")
	void publishedRegistersTheEvent() {
		Instant saleStartsAt = NOW.plusSeconds(600);

		listener.on(new EventPublished(Uuid7.next(), NOW, eventId, UUID.randomUUID(), "Tên", "Mô tả", "Nhạc sống", null,
				UUID.randomUUID(), "Sân", UUID.randomUUID(), "TP", UUID.randomUUID(), NOW.plusSeconds(86_400),
				NOW.plusSeconds(90_000), saleStartsAt, 6, 500, true, null, null, null));

		verify(queue).registerEvent(eventId, true, saleStartsAt);
		verifyNoMoreInteractions(queue);
	}

	@Test
	@DisplayName("Tạm dừng, huỷ hoặc kết thúc sự kiện đều đóng phòng chờ")
	void suspendCancelAndEndCloseTheQueue() {
		listener.on(new EventSuspended(Uuid7.next(), NOW, eventId, UUID.randomUUID(), "Tên", "lý do", false));
		listener.on(new EventCancelled(Uuid7.next(), NOW, eventId, UUID.randomUUID(), "Tên", "lý do"));
		listener.on(new EventEnded(Uuid7.next(), NOW, eventId));

		verify(queue, Mockito.times(3)).close(eventId);
		verifyNoMoreInteractions(queue);
	}

}
