package com.ndz.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.ndz.TestTokens;
import com.ndz.queue.client.RedisWaitingRoom;

/**
 * Giữ ghế sự kiện có phòng chờ cần lượt vào (plan Sprint 7 hạng mục 3; BR-04; overview.md §7.1, §10 bước 0) với Redis
 * thật: thiếu lượt vào thì bị từ chối, một lượt vào chỉ tạo một booking, booking lỗi thì lượt vào còn nguyên.
 */
@IntegrationTest
class BookingAdmissionTests {

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
	RedisWaitingRoom room;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	@DisplayName("BR-04: sự kiện high_demand mà chưa có lượt vào → 403 ADMISSION_REQUIRED, không giữ ghế nào")
	void holdingWithoutAnAdmissionIsRejected() throws Exception {
		UUID event = highDemandEvent();
		List<UUID> seats = seats(event, 2);

		hold(UUID.randomUUID(), event, seats).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMISSION_REQUIRED"));

		assertThat(count("select count(*) from booking.bookings")).isZero();
		assertThat(count("select count(*) from booking.seat_inventory where status <> 'AVAILABLE'")).isZero();
	}

	@Test
	@DisplayName("AC-3: một lượt vào chỉ tạo được 1 booking; lần hai → 403 ADMISSION_REQUIRED")
	void anAdmissionIsGoodForOneBookingOnly() throws Exception {
		UUID event = highDemandEvent();
		List<UUID> seats = seats(event, 3);
		UUID user = UUID.randomUUID();
		admit(event, user);

		hold(user, event, seats.subList(0, 1)).andExpect(status().isCreated());

		assertThat(room.hasAdmission(event, user)).isFalse();
		assertThat(redis.opsForZSet().score(RedisWaitingRoom.admittedKey(event), user.toString())).isNull();
		hold(user, event, seats.subList(1, 2)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMISSION_REQUIRED"));
		assertThat(count("select count(*) from booking.bookings where user_id = '" + user + "'")).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-3: booking lỗi vì ghế bận thì lượt vào còn nguyên để chọn lại")
	void aFailedHoldKeepsTheAdmission() throws Exception {
		UUID event = highDemandEvent();
		List<UUID> seats = seats(event, 2);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		admit(event, first);
		admit(event, second);
		hold(first, event, seats.subList(0, 1)).andExpect(status().isCreated());

		hold(second, event, seats.subList(0, 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		assertThat(room.hasAdmission(event, second)).isTrue();
		hold(second, event, seats.subList(1, 2)).andExpect(status().isCreated());
		assertThat(room.hasAdmission(event, second)).isFalse();
	}

	@Test
	@DisplayName("Booking tạo xong được tính vào pending của phòng chờ tới hạn giữ ghế + ân hạn (BR-03)")
	void aCreatedBookingOccupiesRoomInTheQueue() throws Exception {
		UUID event = highDemandEvent();
		UUID user = UUID.randomUUID();
		admit(event, user);

		hold(user, event, seats(event, 1)).andExpect(status().isCreated());

		// Hạn giữ ghế 10 phút + ân hạn thanh toán 5 phút.
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				redis.opsForZSet().range(RedisWaitingRoom.pendingKey(event), 0, -1)).hasSize(1));
		assertThat(redis.opsForZSet().rangeWithScores(RedisWaitingRoom.pendingKey(event), 0, -1))
				.allSatisfy(entry -> assertThat(entry.getScore())
						.isEqualTo((double) NOW.plus(Duration.ofMinutes(15)).toEpochMilli()));
	}

	// ---- Hỗ trợ ----

	private void admit(UUID event, UUID user) {
		redis.opsForValue().set(RedisWaitingRoom.admitKey(event, user),
				Long.toString(NOW.plus(Duration.ofMinutes(15)).toEpochMilli()));
		redis.opsForZSet().add(RedisWaitingRoom.admittedKey(event), user.toString(),
				NOW.plus(Duration.ofMinutes(15)).toEpochMilli());
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private ResultActions hold(UUID user, UUID event, List<UUID> seats) throws Exception {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids)));
	}

	private UUID highDemandEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, high_demand,
				    max_tickets_per_user, platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện nhu cầu cao', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE',
				    true, 6, 500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW));
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

}
