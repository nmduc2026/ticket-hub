package com.ndz.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.queue.client.RedisWaitingRoom;
import com.ndz.queue.service.QueueService;
import com.ndz.queue.service.QueueService.CloseReason;
import com.ndz.shared.util.Uuid7;

/**
 * Phòng chờ: vào hàng, thứ tự, mở / đóng, lấy lại meta (plan Sprint 7 hạng mục 1; BR-04; overview.md §7.1) với Redis
 * thật.
 */
@IntegrationTest
class QueueJoinTests {

	private static final Instant NOW = Instant.parse("2026-12-01T03:00:00Z");
	private static final Instant SALE_STARTS_AT = NOW.plus(Duration.ofMinutes(10));

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

	@Autowired
	QueueService queue;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from catalog.events where title = 'Phòng chờ thử'");
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	@DisplayName("AC-2: 1.000 user vào trước giờ mở bán → thứ tự khác thứ tự vào, và đứng trước người vào sau giờ mở bán")
	void lotteryOrderDiffersFromJoinOrderAndPrecedesLateJoiners() throws Exception {
		UUID event = highDemandEvent();
		List<UUID> users = new ArrayList<>();
		for (int i = 0; i < 1_000; i++) {
			UUID user = Uuid7.next();
			users.add(user);
			join(user, event).andExpect(status().isAccepted()).andExpect(jsonPath("$.phase").value("LOTTERY"));
		}
		clock.setInstant(SALE_STARTS_AT.plusSeconds(1));
		UUID late = Uuid7.next();
		join(late, event).andExpect(status().isAccepted()).andExpect(jsonPath("$.phase").value("QUEUED"));

		List<String> order = redis.opsForZSet().range(RedisWaitingRoom.waitingKey(event), 0, -1).stream().toList();
		assertThat(order).hasSize(1_001);
		assertThat(order.getLast()).isEqualTo(late.toString());
		assertThat(order.subList(0, 1_000)).containsExactlyInAnyOrderElementsOf(users.stream().map(UUID::toString).toList())
				.isNotEqualTo(users.stream().map(UUID::toString).toList());
		queueStatus(late, event).andExpect(jsonPath("$.position").value(1_001));
	}

	@Test
	@DisplayName("AC-2: vào lại (F5, tab mới) không đổi vị trí")
	void joiningAgainKeepsThePosition() throws Exception {
		UUID event = highDemandEvent();
		UUID first = Uuid7.next();
		UUID second = Uuid7.next();
		// Sau giờ mở bán score = thời điểm vào; đồng hồ test đứng yên nên phải cho nó tiến để hai người khác điểm.
		clock.setInstant(SALE_STARTS_AT.plusSeconds(1));
		join(first, event);
		clock.setInstant(SALE_STARTS_AT.plusSeconds(2));
		join(second, event);
		clock.setInstant(SALE_STARTS_AT.plusSeconds(30));

		join(first, event).andExpect(jsonPath("$.position").value(1));
		join(second, event).andExpect(jsonPath("$.position").value(2));
		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.waitingKey(event))).isEqualTo(2);
	}

	@Test
	@DisplayName("Sau giờ mở bán: vị trí kèm thời gian chờ ước tính theo tốc độ gọi người")
	void positionComesWithEstimatedWait() throws Exception {
		UUID event = highDemandEvent();
		UUID user = Uuid7.next();
		for (int i = 0; i < 40; i++) {
			clock.setInstant(SALE_STARTS_AT.plusSeconds(1).plusMillis(i));
			join(Uuid7.next(), event);
		}
		clock.setInstant(SALE_STARTS_AT.plusSeconds(2));

		// 40 người đứng trước, mặc định gọi 20 người / giây → vị trí 41 chờ ceil(41 / 20) = 3 giây.
		join(user, event).andExpect(jsonPath("$.position").value(41)).andExpect(jsonPath("$.estimatedWaitSeconds").value(3));
	}

	@Test
	@DisplayName("AC-3: đang có lượt vào gọi join → ADMITTED, không bị xếp lại vào hàng")
	void joiningWithAnAdmissionReturnsAdmittedWithoutQueueing() throws Exception {
		UUID event = highDemandEvent();
		UUID user = Uuid7.next();
		Instant expiresAt = NOW.plus(Duration.ofMinutes(15));
		redis.opsForValue().set(RedisWaitingRoom.admitKey(event, user), Long.toString(expiresAt.toEpochMilli()));

		join(user, event).andExpect(status().isAccepted()).andExpect(jsonPath("$.phase").value("ADMITTED"))
				.andExpect(jsonPath("$.admissionExpiresAt").value(expiresAt.toString()));
		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.waitingKey(event))).isZero();
		queueStatus(user, event).andExpect(jsonPath("$.phase").value("ADMITTED"));
	}

	@Test
	@DisplayName("Sự kiện không high_demand → 404 QUEUE_NOT_ENABLED")
	void eventWithoutHighDemandHasNoQueue() throws Exception {
		UUID event = event(false, "PUBLISHED");

		join(Uuid7.next(), event).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("QUEUE_NOT_ENABLED"));
	}

	@Test
	@DisplayName("Vào sớm hơn 15 phút trước giờ mở bán → 409 QUEUE_NOT_OPEN; đúng 15 phút thì được vào")
	void queueOpensFifteenMinutesBeforeSale() throws Exception {
		UUID event = highDemandEvent();
		clock.setInstant(SALE_STARTS_AT.minus(Duration.ofMinutes(15)).minusSeconds(1));
		join(Uuid7.next(), event).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUEUE_NOT_OPEN"));

		clock.setInstant(SALE_STARTS_AT.minus(Duration.ofMinutes(15)));
		join(Uuid7.next(), event).andExpect(status().isAccepted());
	}

	@Test
	@DisplayName("Xoá meta → join vẫn chạy, Queue lấy lại từ Catalog và ghi lại")
	void missingMetaIsRebuiltFromCatalog() throws Exception {
		UUID event = highDemandEvent();
		assertThat(redis.hasKey(RedisWaitingRoom.metaKey(event))).isFalse();

		join(Uuid7.next(), event).andExpect(status().isAccepted());

		assertThat(room.meta(event)).hasValueSatisfying(meta -> {
			assertThat(meta.highDemand()).isTrue();
			assertThat(meta.saleStartsAt()).isEqualTo(SALE_STARTS_AT);
		});
	}

	@Test
	@DisplayName("Chưa vào hàng mà hỏi status → 404 QUEUE_NOT_JOINED; sự kiện không có → 404 EVENT_NOT_FOUND")
	void statusBeforeJoiningAndUnknownEvent() throws Exception {
		UUID event = highDemandEvent();

		queueStatus(Uuid7.next(), event).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("QUEUE_NOT_JOINED"));
		join(Uuid7.next(), UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	@DisplayName("Đóng phòng chờ khi sự kiện bị tạm dừng → xoá mọi key queue:{eventId}:*, join sau đó → 409 QUEUE_CLOSED")
	void suspendingTheEventClosesTheQueue() throws Exception {
		UUID event = highDemandEvent();
		UUID waiting = Uuid7.next();
		UUID admitted = Uuid7.next();
		join(waiting, event);
		redis.opsForValue().set(RedisWaitingRoom.admitKey(event, admitted),
				Long.toString(NOW.plus(Duration.ofMinutes(15)).toEpochMilli()));
		redis.opsForZSet().add(RedisWaitingRoom.admittedKey(event), admitted.toString(), 1);
		redis.opsForZSet().add(RedisWaitingRoom.pendingKey(event), UUID.randomUUID().toString(), 1);
		jdbc.update("update catalog.events set status = 'SUSPENDED' where id = ?", event);

		queue.close(event, CloseReason.SUSPENDED);

		assertThat(redis.keys("queue:{" + event + "}:*")).isEmpty();
		join(Uuid7.next(), event).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUEUE_CLOSED"));
		queueStatus(waiting, event).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUEUE_CLOSED"));
	}

	@Test
	@DisplayName("Đóng phòng chờ khi sự kiện kết thúc → join sau đó → 409 QUEUE_CLOSED")
	void endingTheEventClosesTheQueue() throws Exception {
		UUID event = highDemandEvent();
		join(Uuid7.next(), event);
		jdbc.update("update catalog.events set status = 'ENDED' where id = ?", event);

		queue.close(event, CloseReason.ENDED);

		assertThat(redis.keys("queue:{" + event + "}:*")).isEmpty();
		join(Uuid7.next(), event).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUEUE_CLOSED"));
	}

	@Test
	@DisplayName("Người không phải customer không dùng được phòng chờ")
	void onlyCustomersCanJoin() throws Exception {
		UUID event = highDemandEvent();

		mockMvc.perform(post("/api/v1/queue/" + event + "/join").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().roles("ORGANIZER").bearer())).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/queue/" + event + "/join")).andExpect(status().isUnauthorized());
	}

	// ---- Hỗ trợ ----

	private ResultActions join(UUID user, UUID event) throws Exception {
		return mockMvc.perform(post("/api/v1/queue/" + event + "/join").header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private ResultActions queueStatus(UUID user, UUID event) throws Exception {
		return mockMvc.perform(get("/api/v1/queue/" + event + "/status").header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private static String bearer(UUID user) {
		return TestTokens.token().subject(user).bearer();
	}

	private UUID highDemandEvent() {
		return event(true, "PUBLISHED");
	}

	private UUID event(boolean highDemand, String status) {
		UUID id = UUID.randomUUID();
		// Địa điểm và sơ đồ ghế lấy từ dữ liệu mẫu (profile seed); sự kiện đã publish phải đủ các cột này.
		jdbc.update("""
				insert into catalog.events (id, organizer_id, venue_id, seat_map_id, title, status, high_demand,
				    starts_at, ends_at, sale_starts_at, platform_fee_bps)
				select ?, ?, v.id, m.id, 'Phòng chờ thử', ?, ?, ?, ?, ?, 500
				from catalog.venues v join catalog.seat_maps m on m.venue_id = v.id
				limit 1
				""", id, TestTokens.ORGANIZER_ID, status, highDemand, Timestamp.from(SALE_STARTS_AT.plus(Duration.ofDays(30))),
				Timestamp.from(SALE_STARTS_AT.plus(Duration.ofDays(30)).plus(Duration.ofHours(3))),
				Timestamp.from(SALE_STARTS_AT));
		return id;
	}

}
