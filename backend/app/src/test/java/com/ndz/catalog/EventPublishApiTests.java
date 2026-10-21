package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.catalog.event.EventEnded;
import com.ndz.catalog.event.EventOnSale;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventUpdated;
import com.ndz.catalog.event.SeatInventoryReady;
import com.ndz.catalog.service.EventLifecycleService;
import com.ndz.shared.util.Uuid7;

/**
 * Publish và vòng đời sự kiện (BR-05, BR-06, BR-14; overview.md §7.7, §7.9): điều kiện publish, chốt phí, khoá sơ đồ,
 * event phát ra, job chuyển ON_SALE / ENDED theo giờ và theo tồn kho.
 */
@IntegrationTest
@RecordApplicationEvents
class EventPublishApiTests {

	private static final String EVENTS = "/api/v1/organizer/events";
	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String SEAT_MAP = "00000000-0000-7000-8000-0000000c0001";
	private static final String SECTION_A = "00000000-0000-7000-8000-0000000d0001";
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

	@Autowired
	ApplicationEvents applicationEvents;

	@BeforeEach
	void prepare() {
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from catalog.tier_sections");
		jdbc.update("delete from catalog.ticket_tiers");
		jdbc.update("delete from catalog.events");
		jdbc.update("delete from catalog.platform_fee_policies where id <> '00000000-0000-7000-8000-0000000f0001'");
		jdbc.update("update catalog.seat_maps set locked = false");
		jdbc.update("update catalog.organizers set status = 'APPROVED', payout_ready = true where id = ?",
				TestTokens.ORGANIZER_ID);
	}

	// ---- Publish ----

	@Test
	void completeEventIsPublishedWithTheCurrentFeeAndTheSeatMapGetsLocked() throws Exception {
		String id = completeEvent("Đêm nhạc");

		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.platformFeeBps").value(500))
				.andExpect(jsonPath("$.salesInfoLocked").value(true)).andExpect(jsonPath("$.inventoryReady").value(false));

		assertThat(jdbc.queryForObject("select locked from catalog.seat_maps where id = ?", Boolean.class,
				UUID.fromString(SEAT_MAP))).isTrue();
		assertThat(publishedEvents(EventPublished.class, id)).isEqualTo(1L);
	}

	@Test
	void feeIsFixedAtPublishAndLaterPolicyChangesOnlyAffectLaterEvents() throws Exception {
		String first = completeEvent("Publish trước");
		call(post(EVENTS + "/" + first + "/publish"), organizer()).andExpect(jsonPath("$.platformFeeBps").value(500));

		jdbc.update("insert into catalog.platform_fee_policies (id, fee_bps, effective_from, created_by) "
				+ "values (?, 300, now() - interval '1 minute', ?)", Uuid7.next(), TestTokens.ADMIN_ID);
		String second = completeEvent("Publish sau");
		call(post(EVENTS + "/" + second + "/publish"), organizer()).andExpect(jsonPath("$.platformFeeBps").value(300));

		call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(EVENTS + "/" + first),
				organizer()).andExpect(jsonPath("$.platformFeeBps").value(500));
	}

	@Test
	void publishNeedsAnApprovedOrganizerWhoCanReceivePayouts() throws Exception {
		String id = completeEvent("Chờ duyệt");

		jdbc.update("update catalog.organizers set status = 'PENDING' where id = ?", TestTokens.ORGANIZER_ID);
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PUBLISH_NOT_ALLOWED"))
				.andExpect(jsonPath("$.reasons[0]").value("ORGANIZER_NOT_APPROVED"));

		jdbc.update("update catalog.organizers set status = 'APPROVED', payout_ready = false where id = ?",
				TestTokens.ORGANIZER_ID);
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.reasons[0]").value("PAYOUT_NOT_READY"));

		jdbc.update("update catalog.organizers set status = 'SUSPENDED' where id = ?", TestTokens.ORGANIZER_ID);
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.reasons.length()").value(2));
		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	@Test
	void incompleteEventListsWhatIsMissing() throws Exception {
		String id = idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Thiếu\"}"),
				organizer()).andExpect(status().isCreated()));

		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PUBLISH_INCOMPLETE"))
				.andExpect(jsonPath("$.missing")
						.value(org.hamcrest.Matchers.containsInAnyOrder("VENUE", "SEAT_MAP", "STARTS_AT", "ENDS_AT",
								"SALE_STARTS_AT", "TICKET_TIERS", "TIER_SECTIONS")));
		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	@Test
	void eventWithoutAssignedSectionsCannotBePublished() throws Exception {
		String id = createEvent("Chưa gán khu");
		createTier(id);

		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing[0]").value("TIER_SECTIONS"));
	}

	@Test
	void publishingTwiceIsRejectedByTheStateMachine() throws Exception {
		String id = completeEvent("Hai lần");
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());

		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_TRANSITION"));
	}

	@Test
	void eventThatAlreadyEndedCannotBePublished() throws Exception {
		String id = completeEvent("Đã qua");
		clock.setInstant(EVENT_ENDS.plusSeconds(60));

		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_TIMES"));
	}

	@Test
	void anotherOrganizerAndCustomersCannotPublish() throws Exception {
		String id = completeEvent("Của tôi");
		String stranger = TestTokens.token().subject(UUID.randomUUID()).roles("CUSTOMER", "ORGANIZER").bearer();

		call(post(EVENTS + "/" + id + "/publish"), stranger).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		call(post(EVENTS + "/" + id + "/publish"), TestTokens.token().roles("CUSTOMER").bearer())
				.andExpect(status().isForbidden());
		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	@Test
	void displayEditsAfterPublishEmitEventUpdated() throws Exception {
		String id = completeEvent("Tên cũ");
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());

		call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(EVENTS + "/" + id)
				.contentType(MediaType.APPLICATION_JSON).content(body("Tên mới")), organizer())
				.andExpect(status().isOk());

		assertThat(publishedEvents(EventUpdated.class, id)).isEqualTo(1);
	}

	// ---- Vòng đời theo giờ và theo tồn kho (job) ----

	@Test
	void eventStartsSellingOnlyWhenTheSaleHasStartedAndInventoryIsReady() throws Exception {
		String id = completeEvent("Mở bán");
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());

		clock.setInstant(SALE_STARTS.minusSeconds(60));
		lifecycle.advance();
		assertThat(statusOf(id)).isEqualTo("PUBLISHED"); // chưa tới giờ mở bán

		clock.setInstant(SALE_STARTS.plusSeconds(60));
		lifecycle.advance();
		assertThat(statusOf(id)).isEqualTo("PUBLISHED"); // tới giờ nhưng tồn kho chưa sẵn sàng

		announceInventoryReady(id);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select inventory_ready from catalog.events where id = ?", Boolean.class,
						UUID.fromString(id))).isTrue());

		lifecycle.advance();
		assertThat(statusOf(id)).isEqualTo("ON_SALE");
		assertThat(publishedEvents(EventOnSale.class, id)).isEqualTo(1);

		lifecycle.advance(); // chạy lại không đổi gì thêm
		assertThat(publishedEvents(EventOnSale.class, id)).isEqualTo(1);
	}

	@Test
	void inventoryReadyReceivedTwiceGivesTheSameResult() throws Exception {
		String id = completeEvent("Hai lần tồn kho");
		call(post(EVENTS + "/" + id + "/publish"), organizer()).andExpect(status().isOk());

		announceInventoryReady(id);
		announceInventoryReady(id);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select inventory_ready from catalog.events where id = ?", Boolean.class,
						UUID.fromString(id))).isTrue());
		assertThat(statusOf(id)).isEqualTo("PUBLISHED");
	}

	@Test
	void eventsPastTheirEndTimeAreEndedWhateverTheirState() throws Exception {
		String selling = completeEvent("Đang bán");
		call(post(EVENTS + "/" + selling + "/publish"), organizer()).andExpect(status().isOk());
		announceInventoryReady(selling);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select inventory_ready from catalog.events where id = ?", Boolean.class,
						UUID.fromString(selling))).isTrue());
		clock.setInstant(SALE_STARTS.plusSeconds(60));
		lifecycle.advance();
		assertThat(statusOf(selling)).isEqualTo("ON_SALE");
		String neverSold = completeEvent("Chưa từng bán");
		call(post(EVENTS + "/" + neverSold + "/publish"), organizer()).andExpect(status().isOk());

		clock.setInstant(EVENT_ENDS.plusSeconds(60));
		lifecycle.advance();

		assertThat(statusOf(selling)).isEqualTo("ENDED");
		assertThat(statusOf(neverSold)).isEqualTo("ENDED");
		assertThat(publishedEvents(EventEnded.class, selling)).isEqualTo(1);
	}

	@Test
	void draftEventsAreNeverTouchedByTheJob() throws Exception {
		String id = completeEvent("Nháp");
		clock.setInstant(EVENT_ENDS.plusSeconds(3600));

		lifecycle.advance();

		assertThat(statusOf(id)).isEqualTo("DRAFT");
	}

	// ---- helpers ----

	private void announceInventoryReady(String eventId) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(
				new SeatInventoryReady(Uuid7.next(), Instant.now(), UUID.fromString(eventId), 500)));
	}

	/** Số event loại {@code type} của sự kiện đã phát ra trong test này (ghi lại bởi @RecordApplicationEvents). */
	private long publishedEvents(Class<?> type, String eventId) {
		return applicationEvents.stream(type).filter(event -> event.toString().contains(eventId)).count();
	}

	private String statusOf(String eventId) {
		return jdbc.queryForObject("select status from catalog.events where id = ?", String.class,
				UUID.fromString(eventId));
	}

	private String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).username("organizer").roles("CUSTOMER", "ORGANIZER")
				.bearer();
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private String completeEvent(String title) throws Exception {
		String id = createEvent(title);
		String tier = createTier(id);
		call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(EVENTS + "/" + id + "/tier-sections")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assignments\":[{\"sectionId\":\"%s\",\"tierId\":\"%s\"}]}".formatted(SECTION_A, tier)),
				organizer()).andExpect(status().isOk());
		return id;
	}

	private String createEvent(String title) throws Exception {
		return idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body(title)), organizer())
				.andExpect(status().isCreated()));
	}

	private String createTier(String eventId) throws Exception {
		return idOf(call(post(EVENTS + "/" + eventId + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"VIP\",\"priceMinor\":1000000}"), organizer()).andExpect(status().isCreated()));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String body(String title) {
		return """
				{"title":"%s","description":"Mô tả","category":"Âm nhạc","venueId":"%s","seatMapId":"%s",
				"startsAt":"2027-01-02T12:00:00Z","endsAt":"%s","saleStartsAt":"%s"}"""
				.formatted(title, VENUE, SEAT_MAP, EVENT_ENDS, SALE_STARTS);
	}

	@SuppressWarnings("unused")
	private static Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

}
