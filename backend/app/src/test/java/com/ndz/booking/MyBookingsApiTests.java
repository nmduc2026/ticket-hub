package com.ndz.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/** "Đơn của tôi" (plan Sprint 3 việc 8; AC-9; overview.md §11.2): chỉ đơn của mình, mới tạo trước, theo trang. */
@IntegrationTest
class MyBookingsApiTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private final UUID customer = UUID.randomUUID();
	private UUID event;

	@BeforeEach
	void prepare() {
		cleanUp();
		event = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Concert thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", event, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(86_400)), Timestamp.from(NOW.plusSeconds(2 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
	}

	@Test
	@DisplayName("AC-9: chỉ trả đơn của người đang đăng nhập, mới tạo trước, kèm tên sự kiện và giờ diễn")
	void iOnlySeeMyOwnBookingsNewestFirst() throws Exception {
		UUID older = booking(customer, "EXPIRED", 1_000_000, NOW);
		UUID newer = booking(customer, "CONFIRMED", 2_000_000, NOW.plusSeconds(60));
		UUID other = UUID.randomUUID();
		booking(other, "CONFIRMED", 3_000_000, NOW.plusSeconds(120));

		mockMvc.perform(get("/api/v1/me/bookings").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].bookingId").value(newer.toString()))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED")).andExpect(jsonPath("$[0].totalMinor").value(2_000_000))
				.andExpect(jsonPath("$[0].currency").value("VND")).andExpect(jsonPath("$[0].eventTitle").value("Concert thử"))
				.andExpect(jsonPath("$[0].startsAt").value("2026-11-02T03:00:00Z"))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].bookingId").value(older.toString()))
				.andExpect(jsonPath("$[1].status").value("EXPIRED"));
		mockMvc.perform(get("/api/v1/me/bookings").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(other).bearer())).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void paginationLimitsTheNumberOfBookings() throws Exception {
		for (int i = 0; i < 3; i++) {
			booking(customer, "EXPIRED", 1_000_000, NOW.plusSeconds(i));
		}

		mockMvc.perform(get("/api/v1/me/bookings?page=0&size=2").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/v1/me/bookings?page=1&size=2").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void withoutLoginIs401AndWithoutTheCustomerRoleIs403() throws Exception {
		mockMvc.perform(get("/api/v1/me/bookings")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/bookings").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).roles("ORGANIZER").bearer())).andExpect(status().isForbidden());
	}

	private UUID booking(UUID user, String status, long totalMinor, Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email,
				    created_at)
				values (?, ?, ?, ?, ?, ?, 'a@b.c', ?)
				""", id, user, event, status, totalMinor, Timestamp.from(NOW.plusSeconds(600)),
				Timestamp.from(createdAt));
		return id;
	}

}
