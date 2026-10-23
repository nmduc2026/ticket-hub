package com.ndz.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.client.RedisEventDetailCache;
import com.ndz.booking.service.EventCancellationService;
import com.ndz.booking.service.EventProjectionService;
import com.ndz.catalog.event.EventCancelled;
import com.ndz.catalog.event.EventOnSale;
import com.ndz.catalog.event.EventUpdated;
import com.ndz.shared.util.Uuid7;

/**
 * Cache chi tiết sự kiện (plan Sprint 7 hạng mục 6; overview.md §9): chống cache stampede, trả bản cũ khi có người đang
 * rebuild, xoá cache khi sự kiện đổi. Redis và Postgres thật.
 */
@IntegrationTest
class EventDetailCacheTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	MeterRegistry metrics;

	@Autowired
	EventProjectionService projection;

	@Autowired
	EventCancellationService cancellation;

	@Autowired
	TransactionTemplate transactions;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from booking.processed_events where consumer in ('booking.event-snapshots', 'booking.event-cancelled')");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.event_snapshots");
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	@DisplayName("AC-9: 200 request đồng thời khi cache trống → đúng 1 lần đọc DB")
	void concurrentRequestsOnAColdCacheReadTheDatabaseOnce() throws Exception {
		UUID event = event("PUBLISHED");
		double before = count("rebuild");

		List<Integer> statuses = concurrentGets(event, 200);

		assertThat(statuses).hasSize(200).allMatch(code -> code == 200);
		assertThat(count("rebuild") - before).isEqualTo(1);
		assertThat(redis.hasKey(RedisEventDetailCache.key(event))).isTrue();
	}

	@Test
	@DisplayName("AC-9: sửa sự kiện → lần đọc sau thấy bản mới")
	void updatingTheEventEvictsTheCache() throws Exception {
		UUID event = event("PUBLISHED");
		read(event).andExpect(jsonPath("$.title").value("Sự kiện thử"));
		assertThat(redis.hasKey(RedisEventDetailCache.key(event))).isTrue();

		transactions.executeWithoutResult(tx -> projection.onUpdated(new EventUpdated(Uuid7.next(), NOW.plusSeconds(1),
				event, "Tên mới", "Mô tả mới", "Nhạc sống", null, null, null, null)));

		assertThat(redis.hasKey(RedisEventDetailCache.key(event))).isFalse();
		read(event).andExpect(jsonPath("$.title").value("Tên mới"));
	}

	@Test
	@DisplayName("AC-9: tới giờ mở bán → lần đọc sau thấy ON_SALE")
	void goingOnSaleEvictsTheCache() throws Exception {
		UUID event = event("PUBLISHED");
		read(event).andExpect(jsonPath("$.status").value("PUBLISHED"));

		transactions.executeWithoutResult(
				tx -> projection.onOnSale(new EventOnSale(Uuid7.next(), NOW.plusSeconds(1), event)));

		read(event).andExpect(jsonPath("$.status").value("ON_SALE"));
	}

	@Test
	@DisplayName("AC-9: sự kiện bị huỷ → cache bị xoá, lần đọc sau thấy CANCELLED")
	void cancellingTheEventEvictsTheCache() throws Exception {
		UUID event = event("ON_SALE");
		read(event).andExpect(jsonPath("$.status").value("ON_SALE"));

		transactions.executeWithoutResult(tx -> cancellation.onEventCancelled(new EventCancelled(Uuid7.next(),
				NOW.plusSeconds(1), event, TestTokens.ORGANIZER_ID, "Sự kiện thử", "Admin huỷ")));

		assertThat(redis.hasKey(RedisEventDetailCache.key(event))).isFalse();
		read(event).andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	@DisplayName("Bản cache hết hạn mà đang có request khác rebuild → trả bản cũ, không đọc DB")
	void anExpiredEntryIsServedStaleWhileSomeoneElseRebuilds() throws Exception {
		UUID event = event("PUBLISHED");
		read(event).andExpect(jsonPath("$.title").value("Sự kiện thử"));
		jdbc.update("update booking.event_snapshots set title = 'Đổi thẳng trong DB' where event_id = ?", event);
		clock.setInstant(NOW.plus(Duration.ofMinutes(6)));
		redis.opsForValue().set(RedisEventDetailCache.lockKey(event), "request-khac", Duration.ofSeconds(30));
		double stale = count("stale");
		double rebuild = count("rebuild");

		read(event).andExpect(jsonPath("$.title").value("Sự kiện thử"));

		assertThat(count("stale") - stale).isEqualTo(1);
		assertThat(count("rebuild") - rebuild).isZero();
		redis.delete(RedisEventDetailCache.lockKey(event));
		read(event).andExpect(jsonPath("$.title").value("Đổi thẳng trong DB"));
		assertThat(count("rebuild") - rebuild).isEqualTo(1);
	}

	@Test
	@DisplayName("Sự kiện không có → 404 EVENT_NOT_FOUND và không ghi cache")
	void unknownEventsAreNotCached() throws Exception {
		UUID unknown = UUID.randomUUID();

		read(unknown).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		assertThat(redis.hasKey(RedisEventDetailCache.key(unknown))).isFalse();
	}

	// ---- Hỗ trợ ----

	private org.springframework.test.web.servlet.ResultActions read(UUID event) throws Exception {
		return mockMvc.perform(get("/api/v1/events/" + event));
	}

	private double count(String result) {
		return metrics.counter("tickethub.booking.event_cache", "result", result).count();
	}

	private List<Integer> concurrentGets(UUID event, int requests) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < requests; i++) {
				Callable<Integer> request = () -> {
					start.await();
					return mockMvc.perform(get("/api/v1/events/" + event)).andReturn().getResponse().getStatus();
				};
				futures.add(pool.submit(request));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
			return statuses;
		}
	}

	private UUID event(String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, ?, 6, 500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.plusSeconds(3_600)), status, Timestamp.from(NOW));
		return id;
	}

}
