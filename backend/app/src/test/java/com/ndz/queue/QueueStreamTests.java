package com.ndz.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.queue.client.RedisWaitingRoom;
import com.ndz.queue.client.RedisWaitingRoom.Meta;
import com.ndz.queue.service.QueueAdmissionService;
import com.ndz.queue.service.QueueService;
import com.ndz.queue.service.QueueService.CloseReason;
import com.ndz.queue.service.QueueStreamService;
import com.ndz.shared.util.Uuid7;

/**
 * Luồng SSE của phòng chờ (plan Sprint 7 hạng mục 4; BR-04; overview.md §7.1, §12) với Redis thật: gửi trạng thái ngay
 * khi nối, được gọi tên thì nhận {@code admitted}, kết nối lại đúng lúc được gọi tên vẫn nhận {@code admitted}, đóng
 * phòng chờ thì nhận {@code closed} rồi luồng kết thúc.
 */
@IntegrationTest
class QueueStreamTests {

	private static final Instant SALE_STARTS_AT = Instant.parse("2026-12-01T03:00:00Z");

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
	QueueService queue;

	@Autowired
	QueueStreamService streams;

	private UUID event;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(SALE_STARTS_AT.plusSeconds(1));
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
	@DisplayName("Nối luồng gửi ngay vị trí hiện tại; được gọi tên thì nhận admitted mà không cần hỏi lại")
	void positionOnConnectThenAdmittedWhenCalled() throws Exception {
		UUID user = Uuid7.next();
		room.join(event, user, clock.instant().toEpochMilli());

		MvcResult stream = open(user);

		assertThat(waitFor(stream, "event:position")).contains("\"position\":1").contains("\"phase\":\"QUEUED\"");
		admission.admitWaiting();
		assertThat(waitFor(stream, "event:admitted")).contains("admissionExpiresAt");
	}

	@Test
	@DisplayName("AC-4: SSE đứt đúng lúc được gọi tên → nối lại nhận admitted ngay")
	void reconnectingAfterBeingCalledGetsAdmittedImmediately() throws Exception {
		UUID user = Uuid7.next();
		room.join(event, user, clock.instant().toEpochMilli());
		admission.admitWaiting();

		MvcResult stream = open(user);

		assertThat(body(stream)).contains("event:admitted").doesNotContain("event:position");
	}

	@Test
	@DisplayName("Đóng phòng chờ → mọi người trong hàng nhận closed {reason} rồi luồng kết thúc")
	void closingTheQueueSendsClosedAndEndsTheStream() throws Exception {
		UUID user = Uuid7.next();
		room.join(event, user, clock.instant().toEpochMilli());
		MvcResult stream = open(user);
		waitFor(stream, "event:position");

		queue.close(event, CloseReason.SUSPENDED);

		assertThat(waitFor(stream, "event:closed")).contains("\"reason\":\"SUSPENDED\"");
		// closed là sự kiện cuối: server hoàn tất luồng, nên kết quả async đã được đặt (chưa hoàn tất thì getAsyncResult ném lỗi).
		assertThatCode(() -> stream.getAsyncResult(5_000)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Job đẩy vị trí mới cho khách đang nghe khi người đứng trước đã được gọi")
	void positionsArePushedWhenThePeopleAheadLeave() throws Exception {
		UUID first = Uuid7.next();
		UUID second = Uuid7.next();
		room.join(event, first, clock.instant().toEpochMilli());
		room.join(event, second, clock.instant().plusMillis(1).toEpochMilli());
		MvcResult stream = open(second);
		assertThat(body(stream)).contains("\"position\":2");

		room.consumeAdmission(event, first);
		redis.opsForZSet().remove(RedisWaitingRoom.waitingKey(event), first.toString());
		streams.pushPositions();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(body(stream)).contains("\"position\":1"));
	}

	@Test
	@DisplayName("Chưa vào hàng mà nối luồng → 404 QUEUE_NOT_JOINED, không mở luồng")
	void streamWithoutJoiningIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/queue/" + event + "/stream").accept(MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(Uuid7.next()).bearer()))
				.andExpect(status().isNotFound());
	}

	// ---- Hỗ trợ ----

	private MvcResult open(UUID user) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/queue/" + event + "/stream").accept(MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer()))
				.andExpect(request().asyncStarted()).andReturn();
		return result;
	}

	private static String body(MvcResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Chờ tới khi luồng có {@code marker} rồi trả toàn bộ nội dung. */
	private static String waitFor(MvcResult result, String marker) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(body(result)).contains(marker));
		return body(result);
	}

}
