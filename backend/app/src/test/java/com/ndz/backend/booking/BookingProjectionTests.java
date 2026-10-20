package com.ndz.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.MutableClock;
import com.ndz.backend.TestTokens;
import com.ndz.backend.catalog.events.EventPublished;
import com.ndz.backend.catalog.events.EventUpdated;
import com.ndz.backend.catalog.internal.application.EventLifecycleService;
import com.ndz.backend.shared.Uuid7;

/**
 * Booking nghe event của Catalog (plan Sprint 2 việc 5; overview.md §6.2, §7.9, §8.3): sau publish có
 * {@code event_snapshots} và {@code seat_inventory} đúng số ghế của các khu có hạng vé, Catalog nhận lại
 * {@code SeatInventoryReady}; event nhận hai lần cho kết quả như một lần; event cũ không ghi đè dữ liệu mới.
 */
@IntegrationTest
class BookingProjectionTests {

	private static final String EVENTS = "/api/v1/organizer/events";
	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String SEAT_MAP = "00000000-0000-7000-8000-0000000c0001";
	private static final String SECTION_A = "00000000-0000-7000-8000-0000000d0001";
	private static final String SECTION_B = "00000000-0000-7000-8000-0000000d0002";
	private static final Instant SALE_STARTS = Instant.parse("2027-01-01T00:00:00Z");
	private static final Instant EVENT_ENDS = Instant.parse("2027-01-02T15:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	EventLifecycleService lifecycle;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@BeforeEach
	void prepare() {
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		// Chờ các listener bất đồng bộ của test trước chạy xong rồi mới dọn dữ liệu.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select count(*) from shared.event_publication where completion_date is null",
						Integer.class)).isZero());
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from booking.processed_events");
		jdbc.update("delete from shared.event_publication");
		jdbc.update("delete from catalog.tier_sections");
		jdbc.update("delete from catalog.ticket_tiers");
		jdbc.update("delete from catalog.events");
		jdbc.update("update catalog.seat_maps set locked = false");
	}

	@Test
	void publishCreatesTheSnapshotAndInventoryAndCatalogLearnsTheInventoryIsReady() throws Exception {
		String id = publishedEvent("Đêm nhạc", SECTION_A);
		UUID eventId = UUID.fromString(id);

		awaitInventoryReady(eventId);

		Map<String, Object> snapshot = jdbc.queryForMap("select * from booking.event_snapshots where event_id = ?",
				eventId);
		assertThat(snapshot).containsEntry("status", "PUBLISHED").containsEntry("title", "Đêm nhạc")
				.containsEntry("venue_name", "Sài Gòn Arena").containsEntry("city_name", "Thành phố Hồ Chí Minh")
				.containsEntry("platform_fee_bps", 500).containsEntry("max_tickets_per_user", 6);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ?", eventId)).isEqualTo(160);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE' "
				+ "and booking_id is null and tier_name = 'VIP' and price_minor = 1000000 and section_code = 'A'",
				eventId)).isEqualTo(160);
	}

	@Test
	void onlySeatsOfSectionsWithATierAreCreatedAndEachSeatKeepsItsTierAndPrice() throws Exception {
		String id = createCompleteEvent("Hai hạng", Map.of(SECTION_A, 1_000_000L, SECTION_B, 400_000L));
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());
		UUID eventId = UUID.fromString(id);

		awaitInventoryReady(eventId);

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ?", eventId)).isEqualTo(360);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and section_code = 'C'",
				eventId)).isZero(); // khu C không gán hạng vé: không bán
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and section_code = 'A' "
				+ "and price_minor = 1000000", eventId)).isEqualTo(160);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and section_code = 'B' "
				+ "and price_minor = 400000", eventId)).isEqualTo(200);
	}

	@Test
	void receivingTheSamePublishedEventTwiceGivesTheSameResultAsOnce() throws Exception {
		String id = publishedEvent("Hai lần", SECTION_A);
		UUID eventId = UUID.fromString(id);
		awaitInventoryReady(eventId);
		EventPublished original = capturedPublishedEvent(eventId);
		assertThat(count("select count(*) from shared.event_publication where event_type like '%SeatInventoryReady'"
				+ " and serialized_event like ?", "%" + id + "%")).isEqualTo(1);

		// Gửi lại đúng message cũ (cùng messageId), như khi hạ tầng giao lặp.
		transaction.executeWithoutResult(status -> publisher.publishEvent(original));
		awaitNoIncompletePublications();

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ?", eventId)).isEqualTo(160);
		assertThat(count("select count(*) from booking.event_snapshots where event_id = ?", eventId)).isEqualTo(1);
		assertThat(count("select count(*) from booking.processed_events where message_id = ?", original.messageId()))
				.isEqualTo(1);
		assertThat(count("select count(*) from shared.event_publication where event_type like '%SeatInventoryReady'"
				+ " and serialized_event like ?", "%" + id + "%")).isEqualTo(1); // không báo lại lần hai
	}

	@Test
	void reprovisioningNeverTouchesSeatsThatAreAlreadyHeld() throws Exception {
		String id = publishedEvent("Giữ nguyên", SECTION_A);
		UUID eventId = UUID.fromString(id);
		awaitInventoryReady(eventId);
		UUID booking = Uuid7.next();
		insertHeldSeat(eventId, booking);
		EventPublished again = new EventPublished(Uuid7.next(), Instant.now(), eventId, TestTokens.ORGANIZER_ID,
				"Giữ nguyên", null, null, null, UUID.fromString(VENUE), "Sài Gòn Arena", UUID.fromString("00000000-0000-7000-8000-0000000a0001"), "Thành phố Hồ Chí Minh",
				UUID.fromString(SEAT_MAP), SALE_STARTS.plusSeconds(86_400), EVENT_ENDS, SALE_STARTS, 6, 500, false);

		transaction.executeWithoutResult(status -> publisher.publishEvent(again)); // message mới, cùng sự kiện
		awaitNoIncompletePublications();

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ?", eventId)).isEqualTo(160);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD' "
				+ "and booking_id = ?", eventId, booking)).isEqualTo(1);
	}

	@Test
	void displayEditsAndStatusChangesFollowIntoTheSnapshot() throws Exception {
		String id = publishedEvent("Tên cũ", SECTION_A);
		UUID eventId = UUID.fromString(id);
		awaitInventoryReady(eventId);

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body("Tên mới")), organizer())
				.andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(snapshotValue(eventId, "title"))
				.isEqualTo("Tên mới"));

		clock.setInstant(SALE_STARTS.plusSeconds(60));
		lifecycle.advance();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(snapshotValue(eventId, "status"))
				.isEqualTo("ON_SALE"));

		clock.setInstant(EVENT_ENDS.plusSeconds(60));
		lifecycle.advance();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(snapshotValue(eventId, "status"))
				.isEqualTo("ENDED"));
	}

	@Test
	void anOlderEventNeverOverwritesNewerData() throws Exception {
		String id = publishedEvent("Tên hiện tại", SECTION_A);
		UUID eventId = UUID.fromString(id);
		awaitInventoryReady(eventId);
		EventUpdated stale = new EventUpdated(Uuid7.next(), Instant.parse("2020-01-01T00:00:00Z"), eventId,
				"Tên rất cũ", null, null, null);

		transaction.executeWithoutResult(status -> publisher.publishEvent(stale));
		awaitNoIncompletePublications();

		assertThat(snapshotValue(eventId, "title")).isEqualTo("Tên hiện tại");
	}

	// ---- helpers ----

	private void awaitInventoryReady(UUID eventId) {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select inventory_ready from catalog.events where id = ?", Boolean.class, eventId))
				.isTrue());
	}

	private void awaitNoIncompletePublications() {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select count(*) from shared.event_publication where completion_date is null",
						Integer.class)).isZero());
	}

	private EventPublished capturedPublishedEvent(UUID eventId) {
		// Dựng lại event từ bản sao và dữ liệu đã lưu: cùng messageId với event đã xử lý (lấy từ processed_events).
		UUID messageId = jdbc.queryForObject(
				"select message_id from booking.processed_events where consumer = 'booking.event-published'",
				UUID.class);
		Map<String, Object> snapshot = jdbc.queryForMap("select * from booking.event_snapshots where event_id = ?",
				eventId);
		return new EventPublished(messageId,
				((java.sql.Timestamp) snapshot.get("last_event_at")).toInstant(), eventId, TestTokens.ORGANIZER_ID,
				(String) snapshot.get("title"), null, null, null, UUID.fromString(VENUE), "Sài Gòn Arena",
				UUID.fromString("00000000-0000-7000-8000-0000000a0001"), "Thành phố Hồ Chí Minh", UUID.fromString(SEAT_MAP), SALE_STARTS.plusSeconds(86_400), EVENT_ENDS,
				SALE_STARTS, 6, 500, false);
	}

	private void insertHeldSeat(UUID eventId, UUID bookingId) {
		jdbc.update("insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, "
				+ "contact_email) values (?, ?, ?, 'PENDING_PAYMENT', 1000000, now() + interval '10 minutes', "
				+ "'a@b.c')", bookingId, UUID.randomUUID(), eventId);
		jdbc.update("update booking.seat_inventory set status = 'HELD', booking_id = ? where event_id = ? and "
				+ "seat_id = (select seat_id from booking.seat_inventory where event_id = ? limit 1)", bookingId,
				eventId, eventId);
	}

	private String snapshotValue(UUID eventId, String column) {
		return jdbc.queryForObject("select " + column + " from booking.event_snapshots where event_id = ?",
				String.class, eventId);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private String publishedEvent(String title, String section) throws Exception {
		String id = createCompleteEvent(title, Map.of(section, 1_000_000L));
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());
		return id;
	}

	/** Tạo sự kiện nháp đủ thông tin: một hạng vé cho mỗi khu với giá tương ứng. */
	private String createCompleteEvent(String title, Map<String, Long> priceBySection) throws Exception {
		String id = idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body(title)),
				organizer()).andExpect(status().isCreated()));
		StringBuilder assignments = new StringBuilder();
		for (Map.Entry<String, Long> entry : priceBySection.entrySet()) {
			String tier = idOf(call(post(EVENTS + "/" + id + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"%s\",\"priceMinor\":%d}".formatted(
							priceBySection.size() == 1 ? "VIP" : "Hạng " + entry.getKey(), entry.getValue())),
					organizer()).andExpect(status().isCreated()));
			if (!assignments.isEmpty()) {
				assignments.append(',');
			}
			assignments.append("{\"sectionId\":\"%s\",\"tierId\":\"%s\"}".formatted(entry.getKey(), tier));
		}
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content("{\"assignments\":[" + assignments + "]}"), organizer()).andExpect(status().isOk());
		return id;
	}

	private String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).username("organizer").roles("CUSTOMER", "ORGANIZER")
				.bearer();
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String body(String title) {
		return """
				{"title":"%s","description":"Mô tả","category":"Âm nhạc","venueId":"%s","seatMapId":"%s",
				"startsAt":"%s","endsAt":"%s","saleStartsAt":"%s"}"""
				.formatted(title, VENUE, SEAT_MAP, SALE_STARTS.plusSeconds(86_400), EVENT_ENDS, SALE_STARTS);
	}

	@SuppressWarnings("unused")
	private static List<String> unused() {
		return List.of();
	}

}
