package com.ndz.backend.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.TestTokens;

/**
 * API công khai về sự kiện (plan Sprint 2 việc 6, AC-5; overview.md §11.2): khách chưa đăng nhập xem danh sách, lọc,
 * chi tiết và sơ đồ ghế kèm trạng thái; khu không gán hạng vé là "không bán". Dữ liệu dựng thẳng vào bảng của Booking
 * (đường publish đã có test riêng).
 */
@IntegrationTest
class PublicEventApiTests {

	private static final UUID HCM = UUID.fromString("00000000-0000-7000-8000-0000000a0001");
	private static final UUID HANOI = UUID.fromString("00000000-0000-7000-8000-0000000a0002");
	private static final UUID SEAT_MAP = UUID.fromString("00000000-0000-7000-8000-0000000c0001");
	private static final UUID SECTION_A = UUID.fromString("00000000-0000-7000-8000-0000000d0001");
	private static final Instant IN_TWO_MONTHS = Instant.now().plusSeconds(60L * 86_400);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void prepare() {
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
	}

	// ---- Danh sách ----

	@Test
	void guestSeesOnlyListedEventsOrderedByStartTime() throws Exception {
		UUID later = snapshot("Sau", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS.plusSeconds(86_400));
		UUID sooner = snapshot("Trước", HCM, "Âm nhạc", "PUBLISHED", IN_TWO_MONTHS);
		snapshot("Tạm dừng", HCM, "Âm nhạc", "SUSPENDED", IN_TWO_MONTHS.plusSeconds(2 * 86_400));
		snapshot("Đã kết thúc", HCM, "Âm nhạc", "ENDED", IN_TWO_MONTHS);
		snapshot("Đã huỷ", HCM, "Âm nhạc", "CANCELLED", IN_TWO_MONTHS);
		UUID past = snapshot("Đã qua giờ kết thúc", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		jdbc.update("update booking.event_snapshots set ends_at = now() - interval '1 hour' where event_id = ?", past);

		mockMvc.perform(get("/api/v1/events")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.items[0].id").value(sooner.toString()))
				.andExpect(jsonPath("$.items[1].id").value(later.toString()))
				.andExpect(jsonPath("$.items[2].title").value("Tạm dừng"))
				.andExpect(jsonPath("$.items[2].status").value("SUSPENDED"));
	}

	@Test
	void listShowsTheLowestTierPriceOrNullBeforeInventoryExists() throws Exception {
		UUID priced = snapshot("Có giá", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		snapshot("Chưa tồn kho", HCM, "Âm nhạc", "PUBLISHED", IN_TWO_MONTHS.plusSeconds(86_400));
		List<UUID> seats = seatsOfSectionA(3);
		inventory(priced, seats.get(0), "VIP", 2_000_000, "AVAILABLE");
		inventory(priced, seats.get(1), "Thường", 500_000, "AVAILABLE");

		mockMvc.perform(get("/api/v1/events")).andExpect(jsonPath("$.items[0].minPriceMinor").value(500_000))
				.andExpect(jsonPath("$.items[0].currency").value("VND"))
				.andExpect(jsonPath("$.items[1].minPriceMinor").doesNotExist());
	}

	@Test
	void searchMatchesTitleOrVenueIgnoringCaseAndTreatsPercentLiterally() throws Exception {
		snapshot("Đêm nhạc Rock", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		snapshot("Hài kịch tối", HCM, "Sân khấu", "ON_SALE", IN_TWO_MONTHS.plusSeconds(86_400));
		snapshot("Giảm 100% vé", HCM, "Khác", "ON_SALE", IN_TWO_MONTHS.plusSeconds(2 * 86_400));

		mockMvc.perform(get("/api/v1/events").param("q", "ROCK")).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Đêm nhạc Rock"));
		mockMvc.perform(get("/api/v1/events").param("q", "sài gòn arena")).andExpect(jsonPath("$.total").value(3));
		mockMvc.perform(get("/api/v1/events").param("q", "100%")).andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(get("/api/v1/events").param("q", "%")).andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(get("/api/v1/events").param("q", "không có")).andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void filtersByCityCategoryAndDateRange() throws Exception {
		snapshot("HCM nhạc", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		snapshot("Hà Nội nhạc", HANOI, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS.plusSeconds(86_400));
		snapshot("HCM hài", HCM, "Sân khấu", "ON_SALE", IN_TWO_MONTHS.plusSeconds(5 * 86_400));

		mockMvc.perform(get("/api/v1/events").param("cityId", HANOI.toString())).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Hà Nội nhạc"));
		mockMvc.perform(get("/api/v1/events").param("category", "Sân khấu")).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].title").value("HCM hài"));
		mockMvc.perform(get("/api/v1/events").param("cityId", HCM.toString()).param("category", "Âm nhạc"))
				.andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].title").value("HCM nhạc"));
		mockMvc.perform(get("/api/v1/events").param("from", IN_TWO_MONTHS.plusSeconds(3600).toString()).param("to",
				IN_TWO_MONTHS.plusSeconds(3 * 86_400).toString())).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Hà Nội nhạc"));
	}

	@Test
	void listIsPaginated() throws Exception {
		for (int i = 0; i < 5; i++) {
			snapshot("Sự kiện " + i, HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS.plusSeconds(i * 3600L));
		}

		mockMvc.perform(get("/api/v1/events").param("size", "2").param("page", "1")).andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.items[0].title").value("Sự kiện 2"));
	}

	// ---- Chi tiết ----

	@Test
	void detailShowsTiersAvailabilityAndTheTicketLimit() throws Exception {
		UUID event = snapshot("Chi tiết", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		List<UUID> seats = seatsOfSectionA(4);
		inventory(event, seats.get(0), "VIP", 2_000_000, "AVAILABLE");
		inventory(event, seats.get(1), "VIP", 2_000_000, "SOLD");
		inventory(event, seats.get(2), "Thường", 500_000, "AVAILABLE");
		inventory(event, seats.get(3), "Thường", 500_000, "AVAILABLE");

		mockMvc.perform(get("/api/v1/events/" + event)).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Chi tiết")).andExpect(jsonPath("$.status").value("ON_SALE"))
				.andExpect(jsonPath("$.maxTicketsPerUser").value(6)).andExpect(jsonPath("$.venueName").value("Sài Gòn Arena"))
				.andExpect(jsonPath("$.soldOut").value(false)).andExpect(jsonPath("$.tiers.length()").value(2))
				.andExpect(jsonPath("$.tiers[0].name").value("VIP")).andExpect(jsonPath("$.tiers[0].priceMinor").value(2_000_000))
				.andExpect(jsonPath("$.tiers[0].available").value(1)).andExpect(jsonPath("$.tiers[1].available").value(2));
	}

	@Test
	void soldOutIsComputedFromAvailableSeatsAndComesBackWhenSeatsAreFreed() throws Exception {
		UUID event = snapshot("Hết vé", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		UUID seat = seatsOfSectionA(1).get(0);
		inventory(event, seat, "VIP", 2_000_000, "SOLD");

		mockMvc.perform(get("/api/v1/events/" + event)).andExpect(jsonPath("$.soldOut").value(true));

		jdbc.update("update booking.seat_inventory set status = 'AVAILABLE', booking_id = null where event_id = ?", event);
		mockMvc.perform(get("/api/v1/events/" + event)).andExpect(jsonPath("$.soldOut").value(false));
	}

	@Test
	void endedEventsStayViewableByLinkButUnknownOnesAre404() throws Exception {
		UUID ended = snapshot("Đã kết thúc", HCM, "Âm nhạc", "ENDED", IN_TWO_MONTHS);

		mockMvc.perform(get("/api/v1/events/" + ended)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ENDED"));
		mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	// ---- Sơ đồ ghế ----

	@Test
	void seatMapCarriesSeatStatusesAndMarksUnassignedSectionsAsNotForSale() throws Exception {
		UUID event = snapshot("Sơ đồ", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);
		List<UUID> seats = seatsOfSectionA(3);
		inventory(event, seats.get(0), "VIP", 2_000_000, "AVAILABLE");
		inventory(event, seats.get(1), "VIP", 2_000_000, "HELD");
		inventory(event, seats.get(2), "VIP", 2_000_000, "SOLD");

		mockMvc.perform(get("/api/v1/events/" + event + "/seats")).andExpect(status().isOk())
				.andExpect(jsonPath("$.width").value(1200)).andExpect(jsonPath("$.height").value(800))
				.andExpect(jsonPath("$.sections.length()").value(3)).andExpect(jsonPath("$.elements[0].type").value("STAGE"))
				.andExpect(jsonPath("$.seats.length()").value(500))
				.andExpect(jsonPath("$.sections[?(@.code == 'A')].tierId").isNotEmpty())
				.andExpect(jsonPath("$.sections[?(@.code == 'B')].tierId").value(org.hamcrest.Matchers.contains((Object) null)))
				.andExpect(jsonPath("$.sections[?(@.code == 'A')].shape.kind").value("GRID"))
				.andExpect(jsonPath("$.seats[?(@.status == 'AVAILABLE')].id").value(seats.get(0).toString()))
				.andExpect(jsonPath("$.seats[?(@.status == 'HELD')].id").value(seats.get(1).toString()))
				.andExpect(jsonPath("$.seats[?(@.status == 'SOLD')].id").value(seats.get(2).toString()))
				.andExpect(jsonPath("$.seats[?(@.status == 'NOT_FOR_SALE')]", org.hamcrest.Matchers.hasSize(497)));
	}

	@Test
	void seatMapOfUnknownEventIs404() throws Exception {
		mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID() + "/seats")).andExpect(status().isNotFound());
	}

	// ---- Danh mục tỉnh / xã ----

	@Test
	void administrativeUnitsArePublicAndFilterableByLevelParentAndName() throws Exception {
		mockMvc.perform(get("/api/v1/administrative-units").param("level", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].name").value("Thành phố Hà Nội"))
				.andExpect(jsonPath("$[1].name").value("Thành phố Hồ Chí Minh")).andExpect(jsonPath("$[1].level").value(1))
				.andExpect(jsonPath("$[1].parentId").doesNotExist());
		mockMvc.perform(get("/api/v1/administrative-units").param("level", "2").param("parentId", HCM.toString()))
				.andExpect(jsonPath("$.length()").value(3));
		mockMvc.perform(get("/api/v1/administrative-units").param("q", "bến"))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Phường Bến Nghé"));
	}

	@Test
	void loggedInUsersCanAlsoUseThePublicApi() throws Exception {
		snapshot("Ai cũng xem", HCM, "Âm nhạc", "ON_SALE", IN_TWO_MONTHS);

		mockMvc.perform(get("/api/v1/events").header("Authorization", TestTokens.token().bearer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
	}

	// ---- helpers ----

	private UUID snapshot(String title, UUID cityId, String category, String status, Instant startsAt) {
		UUID id = UUID.randomUUID();
		String cityName = cityId.equals(HCM) ? "Thành phố Hồ Chí Minh" : "Thành phố Hà Nội";
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, description, category, venue_id,
				    venue_name, city_id, city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status,
				    max_tickets_per_user, platform_fee_bps, last_event_at)
				values (?, ?, ?, 'Mô tả', ?, ?, 'Sài Gòn Arena', ?, ?, ?, ?, ?, now() - interval '1 day', ?, 6, 500, now())
				""", id, TestTokens.ORGANIZER_ID, title, category,
				UUID.fromString("00000000-0000-7000-8000-0000000b0001"), cityId, cityName, SEAT_MAP,
				Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(3 * 3600)), status);
		return id;
	}

	private List<UUID> seatsOfSectionA(int count) {
		return jdbc.queryForList("select id from catalog.seats where section_id = ? order by row_label, seat_number "
				+ "limit ?", UUID.class, SECTION_A, count);
	}

	private void inventory(UUID eventId, UUID seatId, String tierName, long priceMinor, String status) {
		UUID tierId = UUID.nameUUIDFromBytes((eventId + tierName).getBytes());
		UUID bookingId = null;
		if (!"AVAILABLE".equals(status)) {
			bookingId = UUID.randomUUID();
			jdbc.update("insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, "
					+ "contact_email) values (?, ?, ?, 'CONFIRMED', 1, now() + interval '1 day', 'a@b.c')", bookingId,
					UUID.randomUUID(), eventId);
		}
		jdbc.update("""
				insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
				    seat_number, price_minor, status, booking_id)
				values (?, ?, ?, ?, 'A', 'A', 1, ?, ?, ?)
				""", eventId, seatId, tierId, tierName, priceMinor, status, bookingId);
	}

	/** Thêm thành phố thứ hai chỉ khi test cần (seed chỉ có TP.HCM). */
	@BeforeEach
	void ensureSecondCity() {
		jdbc.update("""
				insert into catalog.administrative_units (id, code, name, type, level, parent_id, effective_from)
				values (?, 'TEST-01', 'Thành phố Hà Nội', 'THANH_PHO', 1, null, '2025-07-01')
				on conflict (id) do nothing
				""", HANOI);
	}

	@AfterEach
	void removeSecondCity() {
		jdbc.update("delete from catalog.administrative_units where id = ?", HANOI);
	}

}
