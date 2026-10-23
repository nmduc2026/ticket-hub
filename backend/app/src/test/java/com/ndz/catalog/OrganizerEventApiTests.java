package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * API sự kiện của organizer (overview.md §11.2): quyền sở hữu (IDOR), role, khoá thông tin bán vé sau publish
 * (BR-14), hạng vé và việc gán hạng vé cho khu. Không dùng transaction của test để chạy giống production; dữ liệu
 * test được dọn trước và sau mỗi test.
 */
@IntegrationTest
class OrganizerEventApiTests {

	private static final String EVENTS = "/api/v1/organizer/events";
	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String SEAT_MAP = "00000000-0000-7000-8000-0000000c0001";
	private static final String SECTION_A = "00000000-0000-7000-8000-0000000d0001";
	private static final String SECTION_B = "00000000-0000-7000-8000-0000000d0002";
	private static final String SECTION_C = "00000000-0000-7000-8000-0000000d0003";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private final UUID otherOrganizerId = UUID.fromString("00000000-0000-7000-8000-0000000aa002");

	@BeforeEach
	void prepare() {
		cleanUp();
		jdbc.update("insert into catalog.organizers (id, display_name, status, payout_ready) "
				+ "values (?, 'Ban tổ chức khác', 'APPROVED', true)", otherOrganizerId);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from catalog.tier_sections");
		jdbc.update("delete from catalog.ticket_tiers");
		jdbc.update("delete from catalog.events");
		jdbc.update("delete from catalog.organizers where id = ?", otherOrganizerId);
	}

	// ---- Role và đăng nhập ----

	@Test
	void requestWithoutTokenIs401() throws Exception {
		mockMvc.perform(get(EVENTS)).andExpect(status().isUnauthorized());
	}

	@Test
	void customerCannotUseOrganizerApi() throws Exception {
		mockMvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, customer()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(EVENTS).header(HttpHeaders.AUTHORIZATION, customer())
				.contentType(MediaType.APPLICATION_JSON).content(body("Sự kiện"))).andExpect(status().isForbidden());
	}

	@Test
	void organizerWithoutAnOrganizerRecordCannotCreateEvents() throws Exception {
		String stranger = TestTokens.token().subject(UUID.randomUUID()).roles("CUSTOMER", "ORGANIZER").bearer();

		mockMvc.perform(post(EVENTS).header(HttpHeaders.AUTHORIZATION, stranger)
				.contentType(MediaType.APPLICATION_JSON).content(body("Sự kiện")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ORGANIZER_NOT_REGISTERED"));
	}

	// ---- Tạo và xem ----

	@Test
	void organizerCreatesADraftAndReadsItBack() throws Exception {
		String id = createEvent("Đêm nhạc Rock");

		call(get(EVENTS + "/" + id), organizer())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.title").value("Đêm nhạc Rock"))
				.andExpect(jsonPath("$.venueName").value("Sài Gòn Arena"))
				.andExpect(jsonPath("$.maxTicketsPerUser").value(6))
				.andExpect(jsonPath("$.salesInfoLocked").value(false))
				.andExpect(jsonPath("$.tiers.length()").value(0));
	}

	@Test
	void invalidInputIsRejectedWithBusinessCodes() throws Exception {
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body("   ")), organizer())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content(body("X", "2027-01-02T12:00:00Z", "2027-01-02T10:00:00Z", "2027-01-01T00:00:00Z")),
				organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_TIMES"));
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content(body("X", "2027-01-02T10:00:00Z", "2027-01-02T12:00:00Z", "2027-01-03T00:00:00Z")),
				organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_TIMES"));
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithVenue("X", UUID.randomUUID().toString(), null)), organizer())
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"));
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithVenue("X", VENUE, UUID.randomUUID().toString())), organizer())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SEAT_MAP_NOT_IN_VENUE"));
	}

	// ---- Chống IDOR ----

	@Test
	void anotherOrganizerCannotSeeOrChangeMyEvent() throws Exception {
		String id = createEvent("Của tôi");
		String intruder = otherOrganizer();

		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] { get(EVENTS + "/" + id),
				put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body("Hack")),
				get(EVENTS + "/" + id + "/ticket-tiers"),
				post(EVENTS + "/" + id + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
						.content(tierBody("VIP", 1000)),
				put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
						.content("{\"assignments\":[]}") }) {
			call(request, intruder).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		}
		call(get(EVENTS + "/" + id), organizer()).andExpect(jsonPath("$.title").value("Của tôi"));
	}

	@Test
	void listShowsOnlyMyEventsAndSupportsFilters() throws Exception {
		createEvent("Rock Sài Gòn");
		createEvent("Jazz đêm khuya");
		call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body("Của người khác")),
				otherOrganizer()).andExpect(status().isCreated());

		call(get(EVENTS), organizer()).andExpect(jsonPath("$.total").value(2));
		call(get(EVENTS).param("q", "rock"), organizer()).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Rock Sài Gòn"));
		call(get(EVENTS).param("q", "100%"), organizer()).andExpect(jsonPath("$.total").value(0));
		call(get(EVENTS).param("status", "PUBLISHED"), organizer()).andExpect(jsonPath("$.total").value(0));
		call(get(EVENTS).param("status", "DRAFT").param("venueId", VENUE), organizer())
				.andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].venueName").value("Sài Gòn Arena"));
		call(get(EVENTS).param("size", "1"), organizer()).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.total").value(2));
	}

	// ---- Sửa và khoá sau publish (BR-14) ----

	@Test
	void draftAcceptsEveryChange() throws Exception {
		String id = createEvent("Nháp");

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(body("Tên mới", "2027-02-01T12:00:00Z", "2027-02-01T15:00:00Z", "2027-01-15T00:00:00Z")),
				organizer()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Tên mới"))
				.andExpect(jsonPath("$.startsAt").value("2027-02-01T12:00:00Z"));
	}

	@Test
	void afterPublishOnlyDisplayInfoCanChange() throws Exception {
		String id = createEvent("Đã publish");
		publish(id);

		// thông tin hiển thị sửa được
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bodyLike("Tiêu đề mới", "Mô tả mới", "Âm nhạc", null)), organizer())
				.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Tiêu đề mới"))
				.andExpect(jsonPath("$.description").value("Mô tả mới")).andExpect(jsonPath("$.salesInfoLocked").value(true));

		// giờ diễn, giờ mở bán, số vé mỗi người bị khoá
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(body("Đã publish", "2027-03-01T12:00:00Z", "2027-03-01T15:00:00Z", "2027-01-01T00:00:00Z")),
				organizer()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bodyLike("Đã publish", "Mô tả mới", "Âm nhạc", (short) 4)), organizer())
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
	}

	@Test
	@DisplayName("AC-13 (BR-04): organizer bật high_demand khi tạo và sửa nháp; mặc định tắt")
	void highDemandCanBeSwitchedOnWhileDraft() throws Exception {
		String id = createEvent("Phòng chờ nháp");
		call(get(EVENTS + "/" + id), organizer()).andExpect(status().isOk()).andExpect(jsonPath("$.highDemand").value(false));

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(withHighDemand(body("Phòng chờ nháp"), true)),
				organizer()).andExpect(status().isOk()).andExpect(jsonPath("$.highDemand").value(true));
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(withHighDemand(body("Phòng chờ nháp"), false)),
				organizer()).andExpect(status().isOk()).andExpect(jsonPath("$.highDemand").value(false));
	}

	@Test
	@DisplayName("AC-13 (BR-04, BR-14): sau publish không đổi được high_demand, giữ nguyên thì vẫn sửa được thông tin khác")
	void highDemandIsLockedAfterPublish() throws Exception {
		String id = idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content(withHighDemand(body("Phòng chờ khoá"), true)), organizer()).andExpect(status().isCreated()));
		publish(id);

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(withHighDemand(body("Phòng chờ khoá"), false)), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(withHighDemand(body("Tên mới sau publish"), true)), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.highDemand").value(true)).andExpect(jsonPath("$.title").value("Tên mới sau publish"));
	}

	@Test
	@DisplayName("AC-6 (BR-14): sau publish sửa được giờ mở cửa, độ tuổi, quy định; xoá trắng cũng được")
	void afterPublishGuestInfoCanChange() throws Exception {
		String id = createEvent("Thông tin khách");
		publish(id);

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithGuestInfo("\"2027-01-02T10:30:00Z\"", "18", "\"Không mang đồ ăn\"")), organizer())
				.andExpect(status().isOk()).andExpect(jsonPath("$.doorsOpenAt").value("2027-01-02T10:30:00Z"))
				.andExpect(jsonPath("$.ageRestriction").value(18)).andExpect(jsonPath("$.rules").value("Không mang đồ ăn"));
		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithGuestInfo("null", "null", "\"  \"")), organizer())
				.andExpect(status().isOk()).andExpect(jsonPath("$.doorsOpenAt").doesNotExist())
				.andExpect(jsonPath("$.ageRestriction").doesNotExist()).andExpect(jsonPath("$.rules").doesNotExist());
	}

	@Test
	@DisplayName("Giờ mở cửa sau giờ diễn → 422 DOORS_OPEN_AFTER_START, cả nháp lẫn đã publish; độ tuổi 0 → 400")
	void doorsCannotOpenAfterTheShowStarts() throws Exception {
		String draft = createEvent("Mở cửa nháp");
		String published = createEvent("Mở cửa đã publish");
		publish(published);

		for (String id : new String[] { draft, published }) {
			call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
					.content(bodyWithGuestInfo("\"2027-01-02T12:00:01Z\"", "null", "null")), organizer())
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("DOORS_OPEN_AFTER_START"));
		}
		call(put(EVENTS + "/" + draft).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithGuestInfo("\"2027-01-02T12:00:00Z\"", "null", "null")), organizer())
				.andExpect(status().isOk());
		call(put(EVENTS + "/" + draft).contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithGuestInfo("null", "0", "null")), organizer()).andExpect(status().isBadRequest());
	}

	@Test
	void afterPublishTiersAndAssignmentsAreLocked() throws Exception {
		String id = createEvent("Khoá hạng vé");
		String tier = createTier(id, "VIP", 1_000_000);
		publish(id);

		call(post(EVENTS + "/" + id + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
				.content(tierBody("Mới", 1)), organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
		call(put(EVENTS + "/" + id + "/ticket-tiers/" + tier).contentType(MediaType.APPLICATION_JSON)
				.content(tierBody("Sửa", 1)), organizer()).andExpect(status().isConflict());
		call(delete(EVENTS + "/" + id + "/ticket-tiers/" + tier), organizer()).andExpect(status().isConflict());
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, tier)), organizer()).andExpect(status().isConflict());
	}

	@Test
	void endedOrCancelledEventsCannotBeEditedAtAll() throws Exception {
		for (String terminal : new String[] { "ENDED", "CANCELLED" }) {
			String id = createEvent("Kết thúc " + terminal);
			publish(id);
			jdbc.update("update catalog.events set status = ? where id = ?", terminal, UUID.fromString(id));

			call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
					.content(bodyLike("Đổi tên", "mô tả", "Âm nhạc", null)), organizer())
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
		}
	}

	// ---- Hạng vé ----

	@Test
	void tiersCanBeCreatedUpdatedListedAndDeleted() throws Exception {
		String id = createEvent("Hạng vé");
		String vip = createTier(id, "VIP", 2_000_000);
		createTier(id, "Thường", 500_000);

		call(get(EVENTS + "/" + id + "/ticket-tiers"), organizer()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("VIP")).andExpect(jsonPath("$[0].priceMinor").value(2_000_000))
				.andExpect(jsonPath("$[0].currency").value("VND"));
		call(put(EVENTS + "/" + id + "/ticket-tiers/" + vip).contentType(MediaType.APPLICATION_JSON)
				.content(tierBody("VIP+", 2_500_000)), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("VIP+")).andExpect(jsonPath("$.priceMinor").value(2_500_000));
		call(delete(EVENTS + "/" + id + "/ticket-tiers/" + vip), organizer()).andExpect(status().isNoContent());
		call(get(EVENTS + "/" + id), organizer()).andExpect(jsonPath("$.tiers.length()").value(1));
		call(delete(EVENTS + "/" + id + "/ticket-tiers/" + vip), organizer()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TIER_NOT_FOUND"));
	}

	@Test
	void tierPriceCannotBeNegativeAndNameCannotBeBlank() throws Exception {
		String id = createEvent("Giá");

		call(post(EVENTS + "/" + id + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
				.content(tierBody("VIP", -1)), organizer()).andExpect(status().isBadRequest());
		call(post(EVENTS + "/" + id + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
				.content(tierBody(" ", 1000)), organizer()).andExpect(status().isBadRequest());
	}

	// ---- Gán hạng vé cho khu ----

	@Test
	void assignmentsReplaceThePreviousOnesAndUnassignedSectionsAreNotForSale() throws Exception {
		String id = createEvent("Gán khu");
		String vip = createTier(id, "VIP", 2_000_000);
		String normal = createTier(id, "Thường", 500_000);

		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, vip, SECTION_B, normal)), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_C, vip)), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		call(get(EVENTS + "/" + id), organizer()).andExpect(jsonPath("$.tierSections.length()").value(1))
				.andExpect(jsonPath("$.tierSections[0].sectionId").value(SECTION_C));
	}

	@Test
	void deletingATierAlsoRemovesItsAssignments() throws Exception {
		String id = createEvent("Xoá hạng");
		String vip = createTier(id, "VIP", 2_000_000);
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, vip)), organizer()).andExpect(status().isOk());

		call(delete(EVENTS + "/" + id + "/ticket-tiers/" + vip), organizer()).andExpect(status().isNoContent());

		call(get(EVENTS + "/" + id), organizer()).andExpect(jsonPath("$.tierSections.length()").value(0));
	}

	@Test
	void assignmentsAreValidated() throws Exception {
		String id = createEvent("Kiểm gán");
		String other = createEvent("Sự kiện khác");
		String tier = createTier(id, "VIP", 1000);
		String foreignTier = createTier(other, "VIP", 1000);

		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, tier, SECTION_A, tier)), organizer())
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("DUPLICATE_SECTION"));
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(UUID.randomUUID().toString(), tier)), organizer())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SECTION_NOT_IN_SEAT_MAP"));
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, foreignTier)), organizer()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TIER_NOT_FOUND"));
	}

	@Test
	void assigningRequiresASeatMap() throws Exception {
		String id = createEventWithoutVenue("Chưa có sơ đồ");
		String tier = createTier(id, "VIP", 1000);

		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, tier)), organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SEAT_MAP_REQUIRED"));
	}

	@Test
	void changingTheSeatMapClearsAssignmentsOfTheOldMap() throws Exception {
		String id = createEvent("Đổi sơ đồ");
		String tier = createTier(id, "VIP", 1000);
		call(put(EVENTS + "/" + id + "/tier-sections").contentType(MediaType.APPLICATION_JSON)
				.content(assignments(SECTION_A, tier)), organizer()).andExpect(status().isOk());

		call(put(EVENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(bodyWithVenue("Đổi sơ đồ", null,
				null)), organizer()).andExpect(status().isOk());

		call(get(EVENTS + "/" + id), organizer()).andExpect(jsonPath("$.tierSections.length()").value(0));
	}

	// ---- Địa điểm và sơ đồ ghế ----

	@Test
	void organizerCanListVenuesAndTheirSeatMaps() throws Exception {
		call(get("/api/v1/venues"), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Sài Gòn Arena"))
				.andExpect(jsonPath("$[0].cityName").value("Thành phố Hồ Chí Minh"))
				.andExpect(jsonPath("$[0].wardName").value("Phường Bến Nghé"));

		String json = call(get("/api/v1/venues/" + VENUE + "/seat-maps"), organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].sections.length()").value(3))
				.andExpect(jsonPath("$[0].sections[0].shape.kind").value("GRID"))
				.andExpect(jsonPath("$[0].elements[0].type").value("STAGE")).andReturn().getResponse()
				.getContentAsString();
		int seats = JsonPath.<java.util.List<Integer>>read(json, "$[0].sections[*].seatCount").stream()
				.mapToInt(Integer::intValue).sum();
		assertThat(seats).isEqualTo(500);
	}

	@Test
	void venuesAreNotForCustomersAndUnknownVenueIs404() throws Exception {
		call(get("/api/v1/venues"), customer()).andExpect(status().isForbidden());
		call(get("/api/v1/venues/" + UUID.randomUUID() + "/seat-maps"), organizer()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"));
	}

	// ---- helpers ----

	private String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).username("organizer").roles("CUSTOMER", "ORGANIZER")
				.bearer();
	}

	private String otherOrganizer() {
		return TestTokens.token().subject(otherOrganizerId).username("other").roles("CUSTOMER", "ORGANIZER").bearer();
	}

	private String customer() {
		return TestTokens.token().subject(UUID.randomUUID()).roles("CUSTOMER").bearer();
	}

	private ResultActions call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private String createEvent(String title) throws Exception {
		return idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body(title)), organizer())
				.andExpect(status().isCreated()));
	}

	private String createEventWithoutVenue(String title) throws Exception {
		return idOf(call(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"%s\"}".formatted(title)), organizer()).andExpect(status().isCreated()));
	}

	private String createTier(String eventId, String name, long priceMinor) throws Exception {
		return idOf(call(post(EVENTS + "/" + eventId + "/ticket-tiers").contentType(MediaType.APPLICATION_JSON)
				.content(tierBody(name, priceMinor)), organizer()).andExpect(status().isCreated()));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Mô phỏng publish (hạng mục publish làm sau): chỉ đủ để kiểm khoá BR-14. */
	private void publish(String eventId) {
		jdbc.update("update catalog.events set status = 'PUBLISHED', platform_fee_bps = 500 where id = ?",
				UUID.fromString(eventId));
	}

	private static String body(String title) {
		return body(title, "2027-01-02T12:00:00Z", "2027-01-02T15:00:00Z", "2027-01-01T00:00:00Z");
	}

	private static String body(String title, String startsAt, String endsAt, String saleStartsAt) {
		return """
				{"title":"%s","description":"Mô tả","category":"Âm nhạc","venueId":"%s","seatMapId":"%s",
				"startsAt":"%s","endsAt":"%s","saleStartsAt":"%s"}""".formatted(title, VENUE, SEAT_MAP, startsAt, endsAt,
				saleStartsAt);
	}

	/** Cùng nội dung với {@link #body(String)} kèm giờ mở cửa, độ tuổi, quy định (giá trị JSON nguyên văn). */
	private static String bodyWithGuestInfo(String doorsOpenAt, String ageRestriction, String rules) {
		return body("Thông tin khách").replace("}", ",\"doorsOpenAt\":%s,\"ageRestriction\":%s,\"rules\":%s}"
				.formatted(doorsOpenAt, ageRestriction, rules));
	}

	/** Cùng thông tin bán vé với {@link #body(String)}, chỉ đổi thông tin hiển thị (và tuỳ chọn số vé mỗi người). */
	private static String bodyLike(String title, String description, String category, Short maxTickets) {
		return """
				{"title":"%s","description":"%s","category":"%s","venueId":"%s","seatMapId":"%s",
				"startsAt":"2027-01-02T12:00:00Z","endsAt":"2027-01-02T15:00:00Z","saleStartsAt":"2027-01-01T00:00:00Z"%s}"""
				.formatted(title, description, category, VENUE, SEAT_MAP,
						maxTickets == null ? "" : ",\"maxTicketsPerUser\":" + maxTickets);
	}

	private static String bodyWithVenue(String title, String venueId, String seatMapId) {
		return "{\"title\":\"%s\"%s%s}".formatted(title, venueId == null ? "" : ",\"venueId\":\"" + venueId + "\"",
				seatMapId == null ? "" : ",\"seatMapId\":\"" + seatMapId + "\"");
	}

	private static String withHighDemand(String json, boolean highDemand) {
		return json.replace("}", ",\"highDemand\":" + highDemand + "}");
	}

	private static String tierBody(String name, long priceMinor) {
		return "{\"name\":\"%s\",\"priceMinor\":%d}".formatted(name, priceMinor);
	}

	private static String assignments(String... sectionAndTierPairs) {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < sectionAndTierPairs.length; i += 2) {
			if (i > 0) {
				items.append(',');
			}
			items.append("{\"sectionId\":\"%s\",\"tierId\":\"%s\"}".formatted(sectionAndTierPairs[i],
					sectionAndTierPairs[i + 1]));
		}
		return "{\"assignments\":[" + items + "]}";
	}

}
