package com.ndz.booking;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.event.TicketCheckedIn;

/**
 * Số liệu bán vé của organizer (plan Sprint 6 việc 6; AC-10; overview.md §6.2): 3 đơn xác nhận + 1 đơn hoàn → số liệu
 * khớp truy vấn đếm thẳng từ {@code bookings}; ròng đúng công thức; gửi lại event không cộng hai lần; organizer khác
 * → 404; danh sách đơn và CSV có audit.
 */
@IntegrationTest
class OrganizerSalesApiTests {

	private static final String TITLE = "Số liệu thử";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	WireMockServer stripe;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@BeforeEach
	void prepare() {
		cleanUp();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("{\"id\":\"re_sales_1\",\"object\":\"refund\",\"status\":\"succeeded\",\"payment_intent\":\"pi_x\"}")));
	}

	@AfterEach
	void cleanUp() {
		stripe.resetAll();
		for (String table : List.of("payment.processed_events", "payment.refunds", "payment.payments",
				"ticket.processed_events", "ticket.revoked_bookings", "ticket.tickets", "notification.notifications",
				"notification.processed_events", "notification.notification_log", "booking.processed_events",
				"booking.revoked_tickets", "booking.sales_by_event", "booking.booking_items", "booking.seat_inventory",
				"booking.bookings", "shared.idempotency_keys")) {
			jdbc.update("delete from " + table);
		}
		jdbc.update("delete from booking.event_snapshots where title = ?", TITLE);
		jdbc.update("delete from shared.audit_log where action = 'DATA_EXPORTED'");
	}

	@Test
	@DisplayName("AC-10: 3 đơn xác nhận + 1 đơn hoàn → số liệu khớp đếm thẳng; ròng = (gộp − hoàn) − (phí − phí hoàn); check-in gửi lại không cộng hai lần")
	void salesFigures() throws Exception {
		UUID event = onSaleEvent(TestTokens.ORGANIZER_ID);
		List<UUID> vip = seats(event, "VIP", 1_000_000, 4);
		List<UUID> standard = seats(event, "Thường", 500_000, 2);
		UUID a = confirmed(event, List.of(vip.get(0), vip.get(1)));
		UUID b = confirmed(event, List.of(standard.get(0)));
		UUID c = confirmed(event, List.of(vip.get(2), standard.get(1)));
		UUID refunded = confirmed(event, List.of(vip.get(3)));
		refund(refunded);
		TicketCheckedIn checkIn = new TicketCheckedIn(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), a, event,
				vip.get(0), "A1");
		publish(checkIn);
		publish(checkIn);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject(
				"select coalesce(sum(checked_in_count), 0) from booking.sales_by_event where event_id = ?", Integer.class,
				event)).isEqualTo(1));
		long gross = jdbc.queryForObject("select sum(total_minor) from booking.bookings where event_id = ? "
				+ "and status in ('CONFIRMED', 'REFUNDED')", Long.class, event);
		long refundedTotal = jdbc.queryForObject("select total_minor from booking.bookings where id = ?", Long.class,
				refunded);
		// Phí 5% theo hạng: VIP 4 vé × 1.000.000 → 200.000; Thường 2 × 500.000 → 50.000; phần hoàn 1 VIP → 50.000.
		long fee = 250_000;
		long refundedFee = 50_000;

		mockMvc.perform(get("/api/v1/organizer/events/" + event + "/sales").header(HttpHeaders.AUTHORIZATION, organizer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.grossMinor").value(gross))
				.andExpect(jsonPath("$.feeMinor").value(fee)).andExpect(jsonPath("$.refundedMinor").value(refundedTotal))
				.andExpect(jsonPath("$.netMinor").value((gross - refundedTotal) - (fee - refundedFee)))
				.andExpect(jsonPath("$.ticketsSold").value(5)).andExpect(jsonPath("$.capacity").value(6))
				.andExpect(jsonPath("$.checkedIn").value(1))
				.andExpect(jsonPath("$.byTier[?(@.tierName == 'VIP')].sold").value(4))
				.andExpect(jsonPath("$.byTier[?(@.tierName == 'VIP')].refunded").value(1))
				.andExpect(jsonPath("$.byDay.length()").value(1))
				.andExpect(jsonPath("$.byDay[0].grossMinor").value(gross));
		assertThat(List.of(a, b, c)).allSatisfy(id -> assertThat(statusOf(id)).isEqualTo("CONFIRMED"));
	}

	@Test
	@DisplayName("AC-10: organizer khác gọi số liệu / đơn của sự kiện không phải của mình → 404")
	void otherOrganizersSeeNothing() throws Exception {
		UUID event = onSaleEvent(TestTokens.ORGANIZER_ID);
		String stranger = TestTokens.token().subject(UUID.randomUUID()).roles("ORGANIZER").bearer();

		mockMvc.perform(get("/api/v1/organizer/events/" + event + "/sales").header(HttpHeaders.AUTHORIZATION, stranger))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/organizer/bookings?eventId=" + event).header(HttpHeaders.AUTHORIZATION, stranger))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/organizer/bookings").header(HttpHeaders.AUTHORIZATION, stranger))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
	}

	@Test
	@DisplayName("AC-11: danh sách đơn lọc theo trạng thái; CSV cùng bộ lọc, có BOM UTF-8 và 1 dòng audit mỗi lần xuất")
	void bookingListAndCsvExport() throws Exception {
		UUID event = onSaleEvent(TestTokens.ORGANIZER_ID);
		List<UUID> vip = seats(event, "VIP", 1_000_000, 2);
		UUID paid = confirmed(event, List.of(vip.get(0)));
		hold(UUID.randomUUID(), event, List.of(vip.get(1)));

		mockMvc.perform(get("/api/v1/organizer/bookings?status=CONFIRMED").header(HttpHeaders.AUTHORIZATION, organizer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].bookingId").value(paid.toString()))
				.andExpect(jsonPath("$.items[0].ticketCount").value(1));
		String csv = mockMvc.perform(get("/api/v1/organizer/bookings?status=CONFIRMED")
				.header(HttpHeaders.AUTHORIZATION, organizer()).accept("text/csv")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(csv).startsWith("﻿Mã đơn,").contains(paid.toString()).hasLineCount(2);
		assertThat(jdbc.queryForObject("select count(*) from shared.audit_log where action = 'DATA_EXPORTED' "
				+ "and actor_id = ?", Integer.class, TestTokens.ORGANIZER_ID)).isEqualTo(1);
	}

	// ---- helpers ----

	private String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer();
	}

	/** Giữ ghế qua API rồi giả lập Stripe đã thu tiền: Booking xác nhận như thật. */
	private UUID confirmed(UUID event, List<UUID> seats) throws Exception {
		UUID bookingId = hold(UUID.randomUUID(), event, seats);
		long total = jdbc.queryForObject("select total_minor from booking.bookings where id = ?", Long.class, bookingId);
		UUID payment = UUID.randomUUID();
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, 'VND', 'SUCCEEDED', now(), now())
				""", payment, bookingId, "pi_" + bookingId, total, total / 20);
		publish(new PaymentSucceeded(UUID.randomUUID(), Instant.now(), payment, bookingId, total, "VND"));
		await().atMost(Duration.ofSeconds(15)).until(() -> "CONFIRMED".equals(statusOf(bookingId)));
		return bookingId;
	}

	/** Khách yêu cầu hoàn; Payment hoàn qua Stripe giả (succeeded) rồi Booking chuyển REFUNDED. */
	private void refund(UUID bookingId) throws Exception {
		UUID user = jdbc.queryForObject("select user_id from booking.bookings where id = ?", UUID.class, bookingId);
		mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/refund")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20)).until(() -> "REFUNDED".equals(statusOf(bookingId)));
	}

	private UUID hold(UUID user, UUID event, List<UUID> seats) throws Exception {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		String body = mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.bookingId"));
	}

	private void publish(Object event) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(event));
	}

	private String statusOf(UUID bookingId) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, bookingId);
	}

	private UUID onSaleEvent(UUID organizerId) {
		UUID id = UUID.randomUUID();
		Instant startsAt = Instant.now().plusSeconds(30 * 86_400);
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, ?, ?, 'Sài Gòn Arena', ?, 'TP.HCM', ?, ?, ?, ?, 'ON_SALE', 6, 500, ?)
				""", id, organizerId, TITLE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(7200)),
				Timestamp.from(Instant.now().minusSeconds(86_400)), Timestamp.from(Instant.now().minusSeconds(60)));
		return id;
	}

	private List<UUID> seats(UUID event, String tier, long price, int count) {
		UUID tierId = UUID.nameUUIDFromBytes((event + tier).getBytes(StandardCharsets.UTF_8));
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID seat = UUID.randomUUID();
			ids.add(seat);
			jdbc.update("""
					insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
					    seat_number, price_minor)
					values (?, ?, ?, ?, 'A', 'A', ?, ?)
					""", event, seat, tierId, tier, i + 1, price);
		}
		return ids;
	}

}
