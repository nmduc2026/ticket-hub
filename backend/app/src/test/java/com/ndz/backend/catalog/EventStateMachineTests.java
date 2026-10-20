package com.ndz.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.EventContent;
import com.ndz.backend.catalog.internal.domain.EventStatus;
import com.ndz.backend.shared.BusinessException;

/** Máy trạng thái sự kiện (overview.md §7.7): mỗi chuyển hợp lệ và các chuyển không hợp lệ bị từ chối. */
class EventStateMachineTests {

	private static final Instant SALE_STARTS = Instant.parse("2027-01-01T00:00:00Z");
	private static final Instant BEFORE_SALE = SALE_STARTS.minusSeconds(60);
	private static final Instant AFTER_SALE = SALE_STARTS.plusSeconds(60);

	@Test
	void draftPublishesAndLocksTheFee() {
		Event event = draft();

		event.publish(500);

		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPlatformFeeBps()).isEqualTo(500);
	}

	@Test
	void publishedEventStartsSellingOnlyWhenTheSaleHasStartedAndInventoryIsReady() {
		Event event = published();

		assertThat(event.canStartSale(AFTER_SALE)).isFalse(); // chưa có tồn kho
		assertRejected(() -> event.startSale(AFTER_SALE));

		event.markInventoryReady();

		assertThat(event.canStartSale(BEFORE_SALE)).isFalse(); // chưa tới giờ mở bán
		assertRejected(() -> event.startSale(BEFORE_SALE));
		event.startSale(AFTER_SALE);
		assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
	}

	@Test
	void markingInventoryReadyTwiceChangesNothing() {
		Event event = published();

		event.markInventoryReady();
		event.markInventoryReady();

		assertThat(event.isInventoryReady()).isTrue();
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void publishedAndOnSaleEventsCanBeSuspended() {
		Event published = published();
		published.suspend();
		assertThat(published.getStatus()).isEqualTo(EventStatus.SUSPENDED);

		Event onSale = onSale();
		onSale.suspend();
		assertThat(onSale.getStatus()).isEqualTo(EventStatus.SUSPENDED);
	}

	@Test
	void resumeGoesBackToOnSaleOnlyAfterTheSaleStartedWithInventory() {
		Event ready = onSale();
		ready.suspend();
		ready.resume(AFTER_SALE);
		assertThat(ready.getStatus()).isEqualTo(EventStatus.ON_SALE);

		Event early = published();
		early.markInventoryReady();
		early.suspend();
		early.resume(BEFORE_SALE);
		assertThat(early.getStatus()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void publishedOnSaleAndSuspendedEventsCanBeCancelledOrEnded() {
		for (Event event : new Event[] { published(), onSale(), suspended() }) {
			event.cancel();
			assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
		}
		for (Event event : new Event[] { published(), onSale(), suspended() }) {
			event.end();
			assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);
		}
	}

	@Test
	void invalidTransitionsAreRejectedWithConflict() {
		assertRejected(() -> draft().suspend());
		assertRejected(() -> draft().cancel());
		assertRejected(() -> draft().end());
		assertRejected(() -> published().publish(500));
		assertRejected(() -> published().resume(AFTER_SALE));
		assertRejected(() -> onSale().publish(500));

		Event ended = onSale();
		ended.end();
		assertRejected(() -> ended.suspend());
		assertRejected(() -> ended.cancel());
		assertRejected(() -> ended.end());
		assertRejected(() -> ended.resume(AFTER_SALE));

		Event cancelled = published();
		cancelled.cancel();
		assertRejected(() -> cancelled.suspend());
		assertRejected(() -> cancelled.end());
		assertRejected(() -> cancelled.resume(AFTER_SALE));
	}

	@Test
	void rejectionCarriesTheDocumentedCodeAndStatus() {
		assertThatThrownBy(() -> published().publish(500)).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.code()).isEqualTo("INVALID_EVENT_TRANSITION");
			assertThat(e.status().value()).isEqualTo(409);
		});
	}

	// ---- helpers ----

	private static void assertRejected(Runnable transition) {
		assertThatThrownBy(transition::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.code()).isEqualTo("INVALID_EVENT_TRANSITION"));
	}

	private static Event draft() {
		EventContent content = new EventContent("Sự kiện", null, null, UUID.randomUUID(), UUID.randomUUID(),
				SALE_STARTS.plusSeconds(86_400), SALE_STARTS.plusSeconds(100_000), SALE_STARTS, (short) 6);
		return Event.draft(UUID.randomUUID(), UUID.randomUUID(), content, SALE_STARTS.minusSeconds(86_400));
	}

	private static Event published() {
		Event event = draft();
		event.publish(500);
		return event;
	}

	private static Event onSale() {
		Event event = published();
		event.markInventoryReady();
		event.startSale(AFTER_SALE);
		return event;
	}

	private static Event suspended() {
		Event event = onSale();
		event.suspend();
		return event;
	}

}
