package com.ndz.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingCreated;
import com.ndz.booking.event.BookingExpired;
import com.ndz.queue.api.QueueApi;
import com.ndz.queue.client.RedisWaitingRoom;
import com.ndz.queue.client.RedisWaitingRoom.Meta;
import com.ndz.queue.config.QueueProperties;
import com.ndz.queue.service.QueueAdmissionService;
import com.ndz.shared.util.Uuid7;

/**
 * Điều tiết phòng chờ (plan Sprint 7 hạng mục 2; BR-04, BR-03; overview.md §7.1): số người ở trong không vượt
 * {@code max_active}, người bỏ đi thì người kế tiếp được gọi, trước giờ mở bán không gọi ai. Redis thật; đồng hồ test
 * điều khiển từng nhịp.
 */
@IntegrationTest
class QueueAdmissionTests {

	private static final Instant SALE_STARTS_AT = Instant.parse("2026-12-01T03:00:00Z");
	private static final Instant AFTER_SALE = SALE_STARTS_AT.plusSeconds(1);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MutableClock clock;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	RedisWaitingRoom room;

	@Autowired
	QueueAdmissionService admission;

	@Autowired
	QueueApi queueApi;

	@Autowired
	QueueProperties properties;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	private UUID event;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(AFTER_SALE);
		event = UUID.randomUUID();
		room.writeMeta(event, new Meta(true, SALE_STARTS_AT));
		room.markOpen(event);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	@DisplayName("AC-5: không bao giờ có quá max_active người ở trong, mỗi nhịp gọi tối đa admitPerSecond người")
	void neverAdmitsMoreThanMaxActive() {
		List<UUID> users = queue(properties.maxActive() + 100);

		int firstTick = admission.admitWaiting();
		assertThat(firstTick).isEqualTo(properties.admitPerSecond());
		while (admission.admitWaiting() > 0) {
			assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.admittedKey(event))).isLessThanOrEqualTo(properties.maxActive());
		}

		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.admittedKey(event))).isEqualTo(properties.maxActive());
		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.waitingKey(event))).isEqualTo(100);
		assertThat(admission.admitWaiting()).isZero();
		assertThat(users).hasSize(properties.maxActive() + 100);
	}

	@Test
	@DisplayName("Người đứng đầu hàng được gọi trước; lượt vào có key hạn 15 phút và hiện ADMITTED ở status")
	void firstInLineIsAdmittedWithAFifteenMinuteAdmission() throws Exception {
		List<UUID> users = queue(30);

		admission.admitWaiting();

		UUID first = users.getFirst();
		UUID twenty1st = users.get(properties.admitPerSecond());
		Instant expiresAt = AFTER_SALE.plus(properties.admissionTtl());
		assertThat(room.admission(event, first)).contains(expiresAt);
		assertThat(redis.getExpire(RedisWaitingRoom.admitKey(event, first), java.util.concurrent.TimeUnit.SECONDS))
				.isBetween(890L, 900L);
		assertThat(room.admission(event, twenty1st)).isEmpty();
		assertThat(room.rank(event, first)).isEmpty();
		mockMvc.perform(get("/api/v1/queue/" + event + "/status").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(first).bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.phase").value("ADMITTED"))
				.andExpect(jsonPath("$.admissionExpiresAt").value(expiresAt.toString()));
	}

	@Test
	@DisplayName("AC-1: trước giờ mở bán không ai được gọi vào")
	void nobodyIsAdmittedBeforeTheSaleStarts() {
		clock.setInstant(SALE_STARTS_AT.minusSeconds(60));
		queue(50);

		assertThat(admission.admitWaiting()).isZero();

		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.waitingKey(event))).isEqualTo(50);
		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.admittedKey(event))).isZero();
	}

	@Test
	@DisplayName("AC-5: booking chờ thanh toán chiếm chỗ; booking xong thì người kế tiếp được gọi")
	void pendingBookingsOccupyRoomUntilSettled() {
		queue(100);
		List<UUID> bookings = new ArrayList<>();
		for (int i = 0; i < properties.maxActive(); i++) {
			UUID booking = Uuid7.next();
			bookings.add(booking);
			queueApi.bookingPending(event, booking, AFTER_SALE.plus(Duration.ofMinutes(15)));
		}

		assertThat(admission.admitWaiting()).isZero();

		bookings.stream().limit(5).forEach(booking -> queueApi.bookingSettled(event, booking));
		assertThat(admission.admitWaiting()).isEqualTo(5);
		assertThat(admission.admitWaiting()).isZero();
	}

	@Test
	@DisplayName("AC-5: lượt vào hết hạn mà không tạo booking thì người kế tiếp được gọi")
	void expiredAdmissionsMakeRoomForTheNext() {
		queue(properties.maxActive() + 50);
		while (admission.admitWaiting() > 0) {
			// Gọi cho tới khi đầy.
		}
		assertThat(redis.opsForZSet().zCard(RedisWaitingRoom.waitingKey(event))).isEqualTo(50);

		clock.setInstant(AFTER_SALE.plus(properties.admissionTtl()).plusSeconds(1));

		assertThat(admission.admitWaiting()).isEqualTo(properties.admitPerSecond());
	}

	@Test
	@DisplayName("Mất event Booking: phần tử pending vẫn tự hết hạn nên số đếm tự lành")
	void aLostSettlementHealsWhenTheEntryExpires() {
		queue(10);
		for (int i = 0; i < properties.maxActive(); i++) {
			queueApi.bookingPending(event, Uuid7.next(), AFTER_SALE.plus(Duration.ofMinutes(15)));
		}
		assertThat(admission.admitWaiting()).isZero();

		clock.setInstant(AFTER_SALE.plus(Duration.ofMinutes(15)).plusSeconds(1));

		assertThat(admission.admitWaiting()).isEqualTo(10);
	}

	@Test
	@DisplayName("Sự kiện đã đóng (meta bị xoá) thì bị bỏ khỏi danh sách điều tiết, không gọi ai")
	void closedEventsAreDroppedFromTheSchedule() {
		queue(10);
		room.close(event);

		assertThat(admission.admitWaiting()).isZero();

		assertThat(room.openEvents()).doesNotContain(event);
	}

	@Test
	@DisplayName("BookingCreated vào pending tới hạn giữ ghế + ân hạn (BR-03); BookingExpired gỡ ra")
	void bookingEventsMaintainThePendingSet() {
		UUID booking = Uuid7.next();
		Instant expiresAt = AFTER_SALE.plus(Duration.ofMinutes(10));
		publish(new BookingCreated(Uuid7.next(), AFTER_SALE, booking, Uuid7.next(), event, List.of(UUID.randomUUID()),
				100_000, "VND", expiresAt, "a@b.c"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				redis.opsForZSet().score(RedisWaitingRoom.pendingKey(event), booking.toString()))
				.isEqualTo((double) expiresAt.plus(Duration.ofMinutes(5)).toEpochMilli()));

		publish(new BookingExpired(Uuid7.next(), AFTER_SALE, booking, Uuid7.next(), event, List.of()));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				redis.opsForZSet().score(RedisWaitingRoom.pendingKey(event), booking.toString())).isNull());
	}

	// ---- Hỗ trợ ----

	/** Xếp {@code count} người vào hàng chờ, người sau có score lớn hơn (đồng hồ test tiến 1 ms mỗi người). */
	private List<UUID> queue(int count) {
		List<UUID> users = new ArrayList<>();
		Instant start = clock.instant();
		for (int i = 0; i < count; i++) {
			UUID user = Uuid7.next();
			users.add(user);
			room.join(event, user, start.plusMillis(i).toEpochMilli());
		}
		return users;
	}

	private void publish(Object event) {
		transactions.executeWithoutResult(tx -> publisher.publishEvent(event));
	}

}
