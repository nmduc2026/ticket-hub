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

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * Tạm dừng bán (plan Sprint 6 việc 4; AC-7, AC-8; BR-13; overview.md §7.7): đình chỉ organizer tạm dừng mọi sự kiện
 * đang bán, vé đã bán giữ nguyên, giữ ghế mới bị từ chối, mỗi đơn đúng một thông báo, Admin được báo; mở lại theo
 * điều kiện organizer; tạm dừng thủ công / hàng loạt có lý do và audit.
 */
@IntegrationTest
class EventSuspensionApiTests {

	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String SEAT_MAP = "00000000-0000-7000-8000-0000000c0001";
	private static final String TITLE = "Tạm dừng thử";
	private static final String DOMAIN = "@suspension.test";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private UUID admin;

	@BeforeEach
	void prepare() {
		cleanUp();
		admin = UUID.randomUUID();
		jdbc.update("""
				insert into account.user_profiles (user_id, email, full_name, username, roles, enabled, created_at,
				                                   updated_at)
				values (?, ?, 'Admin', 'admin-tam-dung', array['CUSTOMER', 'ADMIN'], true, now(), now())
				""", admin, "admin" + DOMAIN);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notification.notifications");
		jdbc.update("delete from notification.processed_events");
		jdbc.update("delete from notification.notification_log");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.processed_events");
		jdbc.update("delete from booking.event_snapshots where title = ?", TITLE);
		jdbc.update("delete from catalog.events where title = ?", TITLE);
		jdbc.update("delete from shared.audit_log where action in ('EVENT_SUSPENDED', 'EVENT_RESUMED', "
				+ "'ORGANIZER_SUSPENDED', 'ORGANIZER_REINSTATED')");
		jdbc.update("update catalog.organizers set status = 'APPROVED', payout_ready = true, suspension_reason = null "
				+ "where id = ?", TestTokens.ORGANIZER_ID);
		jdbc.update("delete from account.user_profiles where email like ?", "%" + DOMAIN);
	}

	// ---- AC-7: đình chỉ organizer ----

	@Test
	@DisplayName("AC-7: đình chỉ organizer → mọi sự kiện đang bán SUSPENDED, vé giữ nguyên, giữ ghế mới bị từ chối, mỗi đơn 1 thông báo, Admin được báo")
	void suspendingAnOrganizerPausesAllTheirSellingEvents() throws Exception {
		UUID onSale = event("ON_SALE", Instant.now().minusSeconds(3600));
		UUID published = event("PUBLISHED", Instant.now().plusSeconds(86_400));
		UUID ended = event("ENDED", Instant.now().minusSeconds(86_400));
		UUID buyer = UUID.randomUUID();
		UUID paid = booking(onSale, buyer, "CONFIRMED");
		UUID pending = booking(onSale, UUID.randomUUID(), "PENDING_PAYMENT");

		call(post("/api/v1/admin/organizers/" + TestTokens.ORGANIZER_ID + "/suspend"), admin(), "{\"reason\":\"Nghi gian lận\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"))
				.andExpect(jsonPath("$.suspendedEvents").value(2));

		assertThat(statusOf(onSale)).isEqualTo("SUSPENDED");
		assertThat(statusOf(published)).isEqualTo("SUSPENDED");
		assertThat(statusOf(ended)).isEqualTo("ENDED");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(snapshotStatus(onSale)).isEqualTo("SUSPENDED");
			assertThat(notifications("SALE_PAUSED", buyer)).isEqualTo(1);
			assertThat(count("select count(*) from notification.notifications where type = 'SALE_PAUSED' "
					+ "and user_id <> ?", TestTokens.ORGANIZER_ID)).isEqualTo(2);
			assertThat(notifications("SALE_PAUSED", TestTokens.ORGANIZER_ID)).isEqualTo(2);
			assertThat(notifications("EVENT_AUTO_SUSPENDED", admin)).isEqualTo(2);
		});
		assertThat(bookingStatus(paid)).isEqualTo("CONFIRMED");
		assertThat(bookingStatus(pending)).isEqualTo("PENDING_PAYMENT");
		mockMvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, TestTokens.token().bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[\"%s\"]}".formatted(onSale, UUID.randomUUID())))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_ON_SALE"));
	}

	@Test
	@DisplayName("AC-7: mở lại khi organizer còn bị đình chỉ → 409; khôi phục rồi mở lại → ON_SALE nếu đã qua giờ mở bán, ngược lại PUBLISHED")
	void resumingRequiresAReinstatedOrganizer() throws Exception {
		UUID onSale = event("ON_SALE", Instant.now().minusSeconds(3600));
		UUID published = event("PUBLISHED", Instant.now().plusSeconds(86_400));
		call(post("/api/v1/admin/organizers/" + TestTokens.ORGANIZER_ID + "/suspend"), admin(), "{\"reason\":\"Kiểm tra\"}")
				.andExpect(status().isOk());

		call(post("/api/v1/admin/events/" + onSale + "/resume"), admin(), null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORGANIZER_SUSPENDED"));
		call(post("/api/v1/admin/organizers/" + TestTokens.ORGANIZER_ID + "/reinstate"), admin(), "{\"reason\":\"Đã xác minh\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		// Khôi phục organizer không tự mở bán sự kiện.
		assertThat(statusOf(onSale)).isEqualTo("SUSPENDED");

		call(post("/api/v1/admin/events/" + onSale + "/resume"), admin(), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ON_SALE"));
		call(post("/api/v1/admin/events/" + published + "/resume"), admin(), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));
		await().atMost(Duration.ofSeconds(15))
				.untilAsserted(() -> assertThat(snapshotStatus(onSale)).isEqualTo("ON_SALE"));
	}

	@Test
	@DisplayName("AC-7: organizer chưa charges_enabled (payout_ready = false) → mở lại bị từ chối ORGANIZER_PAYOUT_NOT_READY")
	void resumingRequiresPayoutReady() throws Exception {
		UUID onSale = event("ON_SALE", Instant.now().minusSeconds(3600));
		call(post("/api/v1/admin/events/" + onSale + "/suspend"), admin(), "{\"reason\":\"Sự cố mở bán\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
		jdbc.update("update catalog.organizers set payout_ready = false where id = ?", TestTokens.ORGANIZER_ID);

		call(post("/api/v1/admin/events/" + onSale + "/resume"), admin(), null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORGANIZER_PAYOUT_NOT_READY"));
	}

	// ---- AC-8: tạm dừng thủ công ----

	@Test
	@DisplayName("AC-8: tạm dừng không có lý do → 400; bulk-suspend 3 sự kiện (1 đã ENDED) → 2 ok, 1 INVALID_EVENT_TRANSITION; mỗi thao tác có audit")
	void manualAndBulkSuspension() throws Exception {
		UUID first = event("ON_SALE", Instant.now().minusSeconds(3600));
		UUID second = event("PUBLISHED", Instant.now().plusSeconds(86_400));
		UUID ended = event("ENDED", Instant.now().minusSeconds(86_400));

		call(post("/api/v1/admin/events/" + first + "/suspend"), admin(), "{\"reason\":\"\"}")
				.andExpect(status().isBadRequest());
		call(post("/api/v1/admin/events/bulk-suspend"), admin(),
				"{\"ids\":[\"%s\",\"%s\",\"%s\"],\"reason\":\"Bảo trì\"}".formatted(first, second, ended))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[?(@.ok == true)].id").value(org.hamcrest.Matchers.containsInAnyOrder(
						first.toString(), second.toString())))
				.andExpect(jsonPath("$.results[?(@.id == '" + ended + "')].code").value("INVALID_EVENT_TRANSITION"));

		assertThat(count("select count(*) from shared.audit_log where action = 'EVENT_SUSPENDED' and actor_id = ?",
				admin)).isEqualTo(2);
		assertThat(statusOf(ended)).isEqualTo("ENDED");
	}

	@Test
	void onlyAdminsCanSuspend() throws Exception {
		UUID onSale = event("ON_SALE", Instant.now().minusSeconds(3600));
		call(post("/api/v1/admin/events/" + onSale + "/suspend"),
				TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer(), "{\"reason\":\"x\"}")
				.andExpect(status().isForbidden());
		assertThat(statusOf(onSale)).isEqualTo("ON_SALE");
	}

	// ---- helpers ----

	private String admin() {
		return TestTokens.token().subject(admin).roles("ADMIN").bearer();
	}

	private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String bearer, String body) throws Exception {
		request.header(HttpHeaders.AUTHORIZATION, bearer);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	/** Sự kiện đã publish của organizer test kèm bản sao ở Booking cùng trạng thái. */
	private UUID event(String status, Instant saleStartsAt) {
		UUID id = UUID.randomUUID();
		Instant startsAt = Instant.now().plusSeconds(30 * 86_400);
		jdbc.update("""
				insert into catalog.events (id, organizer_id, venue_id, seat_map_id, title, starts_at, ends_at,
				    sale_starts_at, status, platform_fee_bps, inventory_ready)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 500, true)
				""", id, TestTokens.ORGANIZER_ID, UUID.fromString(VENUE), UUID.fromString(SEAT_MAP), TITLE,
				Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(7200)), Timestamp.from(saleStartsAt), status);
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, ?, ?, 'Sài Gòn Arena', ?, 'TP.HCM', ?, ?, ?, ?, ?, 6, 500, ?)
				""", id, TestTokens.ORGANIZER_ID, TITLE, UUID.fromString(VENUE), UUID.randomUUID(),
				UUID.fromString(SEAT_MAP), Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(7200)),
				Timestamp.from(saleStartsAt), status, Timestamp.from(Instant.now().minusSeconds(60)));
		return id;
	}

	private UUID booking(UUID eventId, UUID user, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, 1000000, now() + interval '10 minutes', ?)
				""", id, user, eventId, status, "khach" + DOMAIN);
		return id;
	}

	private String statusOf(UUID eventId) {
		return jdbc.queryForObject("select status from catalog.events where id = ?", String.class, eventId);
	}

	private String snapshotStatus(UUID eventId) {
		return jdbc.queryForObject("select status from booking.event_snapshots where event_id = ?", String.class,
				eventId);
	}

	private String bookingStatus(UUID bookingId) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, bookingId);
	}

	private int notifications(String type, UUID user) {
		return count("select count(*) from notification.notifications where type = ? and user_id = ?", type, user);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

}
