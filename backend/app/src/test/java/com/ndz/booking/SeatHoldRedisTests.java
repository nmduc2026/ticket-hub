package com.ndz.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.RedisControl;
import com.ndz.TestTokens;
import com.ndz.booking.api.BookingApi;
import com.ndz.booking.service.BookingExpiryService;
import com.ndz.booking.service.SeatHoldService;

/**
 * Lớp 1 giữ ghế bằng Redis (plan Sprint 5 việc 3, 4; BR-01, BR-02, BR-03; overview.md §10) với Redis thật: giữ cả nhóm
 * hoặc không ghế nào, nhả đúng key của booking, TTL, Redis chết thì 503 và dựng lại key khi phục hồi, và không bán
 * trùng kể cả khi key mất.
 */
@IntegrationTest
class SeatHoldRedisTests {

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
	SeatHoldService seatHolds;

	@Autowired
	BookingApi bookingApi;

	@Autowired
	BookingExpiryService expiry;

	@Autowired
	RedisControl redisControl;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		seatHolds.setEnabled(true);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			seatHolds.probe();
			assertThat(seatHolds.isAvailable()).isTrue();
		});
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		// Redis có thể đang bị tạm dừng nếu một test hỏng giữa chừng.
		try {
			redisControl.resume();
		}
		catch (RuntimeException ignored) {
			// Đã chạy rồi: không sao.
		}
		seatHolds.setEnabled(false);
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
		flushRedis();
	}

	// ---- Việc 3: giữ nhóm ghế ----

	@Test
	@DisplayName("Giữ ghế đặt key theo booking với TTL = hạn giữ + 30 giây")
	void holdingWritesOneKeyPerSeatOwnedByTheBookingWithTtl() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);

		UUID bookingId = bookingIdOf(hold(UUID.randomUUID(), event, seats));

		for (UUID seat : seats) {
			assertThat(redis.opsForValue().get(key(event, seat))).isEqualTo(bookingId.toString());
			assertThat(redis.getExpire(key(event, seat), java.util.concurrent.TimeUnit.SECONDS))
					.isBetween(600L, 630L);
		}
	}

	@Test
	@DisplayName("AC-3: một ghế trong nhóm đang bận → không ghế nào được giữ, ở cả Redis lẫn Postgres")
	void oneBusySeatMeansNoSeatOfTheGroupIsHeld() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 3);
		UUID first = bookingIdOf(hold(UUID.randomUUID(), event, List.of(seats.get(1))));

		hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		assertThat(redis.hasKey(key(event, seats.get(0)))).isFalse();
		assertThat(redis.hasKey(key(event, seats.get(2)))).isFalse();
		assertThat(redis.opsForValue().get(key(event, seats.get(1)))).isEqualTo(first.toString());
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-3: TX Postgres lỗi → xoá key của đúng booking đó, key của booking khác còn nguyên")
	void whenThePostgresTransactionFailsOnlyThatBookingsKeysAreRemoved() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> real = seats(event, 1);
		UUID other = bookingIdOf(hold(UUID.randomUUID(), event, real));
		// Ghế không có trong tồn kho: Redis cho qua (không biết tồn kho), Postgres từ chối.
		List<UUID> ghosts = List.of(UUID.randomUUID(), UUID.randomUUID());

		hold(UUID.randomUUID(), event, ghosts).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		for (UUID ghost : ghosts) {
			assertThat(redis.hasKey(key(event, ghost))).isFalse();
		}
		assertThat(redis.opsForValue().get(key(event, real.get(0)))).isEqualTo(other.toString());
	}

	@Test
	@DisplayName("AC-3, BR-01: key mất mà Postgres còn HELD → người thứ hai qua được Redis nhưng Postgres chặn, không bán trùng")
	void aLostKeyNeverCausesADoubleHold() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);
		hold(UUID.randomUUID(), event, seats).andExpect(status().isCreated());
		redis.delete(key(event, seats.get(0)));

		hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(1);
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
		assertThat(redis.hasKey(key(event, seats.get(0)))).isFalse(); // key của lần thất bại đã được nhả
	}

	@Test
	@DisplayName("AC-1: 50 luồng tranh 1 ghế với lớp Redis → đúng 1 thắng, Redis chặn phần lớn, không trùng")
	void fiftyThreadsRaceForOneSeat() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);
		int threads = 50;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			UUID user = UUID.randomUUID();
			Callable<Integer> task = () -> {
				go.await();
				return hold(user, event, seats).andReturn().getResponse().getStatus();
			};
			results.add(pool.submit(task));
		}
		go.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get());
		}
		pool.shutdown();

		assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
		assertThat(statuses.stream().filter(s -> s == 409)).hasSize(threads - 1);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(1);
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
	}

	@Test
	@DisplayName("Tắt lớp Redis thì chỉ còn Postgres: giữ ghế vẫn đúng và không ghi key")
	void withTheLayerOffOnlyPostgresDecides() throws Exception {
		seatHolds.setEnabled(false);
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);

		hold(UUID.randomUUID(), event, seats).andExpect(status().isCreated());
		hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict());

		assertThat(redis.hasKey(key(event, seats.get(0)))).isFalse();
	}

	// ---- Việc 3: vòng đời key ----

	@Test
	@DisplayName("AC-4: bắt đầu thanh toán gia hạn key tới hạn ân hạn + 30 giây")
	void startingPaymentExtendsTheKeys() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);
		UUID user = UUID.randomUUID();
		UUID bookingId = bookingIdOf(hold(user, event, seats));

		bookingApi.startPayment(user, bookingId);

		assertThat(redis.getExpire(key(event, seats.get(0)), java.util.concurrent.TimeUnit.SECONDS))
				.isBetween(900L, 930L); // 10 phút + 5 phút ân hạn + 30 giây
	}

	@Test
	@DisplayName("AC-4: khách huỷ booking → key bị xoá ngay, không chờ TTL")
	void cancellingReleasesTheKeysImmediately() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID user = UUID.randomUUID();
		UUID bookingId = bookingIdOf(hold(user, event, seats));

		mockMvc.perform(delete("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isNoContent());

		for (UUID seat : seats) {
			assertThat(redis.hasKey(key(event, seat))).isFalse();
		}
		// Ghế được giữ lại ngay bởi người khác.
		hold(UUID.randomUUID(), event, seats).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("AC-4: booking hết hạn → job nhả key ngay")
	void expiringReleasesTheKeys() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);
		bookingIdOf(hold(UUID.randomUUID(), event, seats));

		clock.setInstant(NOW.plus(Duration.ofMinutes(11)));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);

		assertThat(redis.hasKey(key(event, seats.get(0)))).isFalse();
	}

	// ---- Việc 4: Redis chết → fail-closed ----

	@Test
	@DisplayName("AC-5: Redis chết → 503 SEAT_HOLD_UNAVAILABLE kèm Retry-After, không tạo booking; xem ghế vẫn chạy")
	void whenRedisIsDownHoldingFailsClosedAndNothingIsCreated() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);

		redisControl.pause();
		try {
			hold(UUID.randomUUID(), event, seats).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.code").value("SEAT_HOLD_UNAVAILABLE"))
					.andExpect(header().string("Retry-After", "5"));
			assertThat(seatHolds.isAvailable()).isFalse();
			// Request sau trả 503 ngay, không chờ timeout từng cái.
			long started = System.nanoTime();
			hold(UUID.randomUUID(), event, seats).andExpect(status().isServiceUnavailable());
			assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(300));
			assertThat(count("select count(*) from booking.bookings")).isZero();
			// Xem sự kiện không phụ thuộc Redis.
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.get("/api/v1/events/" + event)).andExpect(status().isOk());
		}
		finally {
			redisControl.resume();
		}
	}

	@Test
	@DisplayName("AC-5: Redis về → key được dựng lại từ các dòng HELD rồi mới mở lại giữ ghế")
	void afterRecoveryKeysAreRebuiltFromPostgresBeforeHoldingResumes() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID user = UUID.randomUUID();
		UUID bookingId = bookingIdOf(hold(user, event, seats));

		redisControl.pause();
		hold(UUID.randomUUID(), event, seats(event, 1)).andExpect(status().isServiceUnavailable());
		redisControl.resume();
		// Redis về với dữ liệu trống (mất hết key).
		flushRedis();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			seatHolds.probe();
			assertThat(seatHolds.isAvailable()).isTrue();
		});

		for (UUID seat : seats) {
			assertThat(redis.opsForValue().get(key(event, seat))).isEqualTo(bookingId.toString());
			assertThat(redis.getExpire(key(event, seat), java.util.concurrent.TimeUnit.SECONDS))
					.isBetween(600L, 630L);
		}
		// Giữ ghế chạy lại và ghế đã giữ vẫn bị chặn.
		hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict());
		hold(UUID.randomUUID(), event, seats(event, 1)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("Redis mất dữ liệu mà không đứt kết nối (FLUSHALL) → lần kiểm tra kế tiếp dựng lại key")
	void flushedRedisIsDetectedAndRebuilt() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 1);
		UUID bookingId = bookingIdOf(hold(UUID.randomUUID(), event, seats));

		flushRedis();
		assertThat(redis.hasKey(key(event, seats.get(0)))).isFalse();
		seatHolds.probe();

		assertThat(redis.opsForValue().get(key(event, seats.get(0)))).isEqualTo(bookingId.toString());
		assertThat(seatHolds.isAvailable()).isTrue();
	}

	// ---- Hỗ trợ ----

	private void flushRedis() {
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	private static String key(UUID event, UUID seat) {
		return "seat:{" + event + "}:" + seat;
	}

	private ResultActions hold(UUID user, UUID event, List<UUID> seats) throws Exception {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids)));
	}

	private static String bearer(UUID user) {
		return TestTokens.token().subject(user).bearer();
	}

	private static UUID bookingIdOf(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.bookingId"));
	}

	private UUID onSaleEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		return id;
	}

	private List<UUID> seats(UUID event, int count) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(UUID.randomUUID());
		}
		ids.sort(Comparator.naturalOrder());
		for (int i = 0; i < count; i++) {
			jdbc.update("""
					insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
					    seat_number, price_minor)
					values (?, ?, ?, 'VIP', 'A', 'A', ?, 1000000)
					""", event, ids.get(i), UUID.nameUUIDFromBytes("VIP".getBytes()), i + 1);
		}
		return ids;
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

}
