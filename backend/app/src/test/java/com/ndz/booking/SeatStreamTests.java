package com.ndz.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.shared.sse.SseChannels;

/**
 * Luồng SSE trạng thái ghế (plan Sprint 7 hạng mục 4, AC-6; overview.md §12) với Redis thật: khách chưa đăng nhập nối
 * được, giữ ghế qua API thì người đang xem nhận {@code HELD} trong vài trăm mili giây, tin từ pod khác (publish thẳng
 * vào Redis) cũng tới.
 */
@IntegrationTest
class SeatStreamTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	StringRedisTemplate redis;

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
	}

	@Test
	@DisplayName("AC-6: A giữ ghế → người đang xem sơ đồ (không cần đăng nhập) nhận HELD trong ≤ 2 giây")
	void viewersSeeSeatsBeingHeldAlmostImmediately() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		MvcResult viewer = openStream(event);

		mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(UUID.randomUUID()).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[\"%s\"]}".formatted(event, seats.get(0))))
				.andExpect(status().isCreated());

		await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(body(viewer))
				.contains("event:seats").contains(seats.get(0).toString()).contains("\"status\":\"HELD\"")
				.doesNotContain(seats.get(1).toString()));
	}

	@Test
	@DisplayName("AC-6: tin do pod khác phát lên Redis cũng tới người đang xem ở pod này")
	void changesPublishedByAnotherPodReachLocalViewers() throws Exception {
		UUID event = onSaleEvent();
		MvcResult viewer = openStream(event);
		UUID seat = UUID.randomUUID();

		// Pod B không có kết nối nào ở đây: nó chỉ publish vào kênh chung.
		redis.convertAndSend(SseChannels.seats(event), "{\"event\":\"seats\",\"data\":\"{\\\"changes\\\":[{\\\"seatId\\\":\\\""
				+ seat + "\\\",\\\"status\\\":\\\"SOLD\\\"}]}\",\"last\":false}");

		await().atMost(Duration.ofSeconds(5)).untilAsserted(
				() -> assertThat(body(viewer)).contains("event:seats").contains(seat.toString()).contains("SOLD"));
	}

	@Test
	@DisplayName("Người xem sự kiện A không nhận thay đổi ghế của sự kiện B")
	void viewersOnlyReceiveTheirOwnEvent() throws Exception {
		UUID eventA = onSaleEvent();
		UUID eventB = onSaleEvent();
		MvcResult viewerA = openStream(eventA);
		MvcResult viewerB = openStream(eventB);
		UUID seat = UUID.randomUUID();

		redis.convertAndSend(SseChannels.seats(eventB), "{\"event\":\"seats\",\"data\":\"{\\\"changes\\\":[{\\\"seatId\\\":\\\""
				+ seat + "\\\",\\\"status\\\":\\\"HELD\\\"}]}\",\"last\":false}");

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(body(viewerB)).contains(seat.toString()));
		assertThat(body(viewerA)).doesNotContain(seat.toString());
	}

	@Test
	@DisplayName("Sự kiện không có → 404 EVENT_NOT_FOUND, không mở luồng")
	void unknownEventIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID() + "/seats/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	// ---- Hỗ trợ ----

	private MvcResult openStream(UUID event) throws Exception {
		return mockMvc.perform(get("/api/v1/events/" + event + "/seats/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted()).andReturn();
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private UUID onSaleEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6, 500, ?)
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

}
