package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingConfirmationFailed;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.service.BookingExpiryService;
import com.ndz.payment.entity.Refund;
import com.ndz.payment.service.PaymentRefundService;

/**
 * Thanh toán đến muộn (plan Sprint 3 việc 5; AC-6; overview.md §7.3, §7.4, §7.7; ADR 0019, 0026): booking đã hết hạn
 * hoặc bị huỷ nhận {@code PaymentSucceeded}; giữ lại được đúng các ghế thì {@code CONFIRMED}, không thì giữ nguyên
 * trạng thái và Payment hoàn 100% với idempotency key {@code refund-{refundId}}. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class LatePaymentTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant AFTER_HOLD = NOW.plusSeconds(601);
	private static final String INTENT_ID = "pi_late_1";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	WireMockServer stripe;

	@Autowired
	EventRecorder events;

	@Autowired
	BookingExpiryService expiry;

	@Autowired
	PaymentRefundService refundService;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	private final UUID customer = UUID.randomUUID();
	private final UUID otherCustomer = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		events.clear();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("succeeded")));
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		stripe.resetAll();
		jdbc.update("delete from payment.processed_events");
		jdbc.update("delete from payment.refunds");
		jdbc.update("delete from payment.stripe_events");
		jdbc.update("delete from payment.payments");
		jdbc.update("delete from booking.processed_events");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
	}

	// ---- Giữ lại được ghế ----

	@Test
	@DisplayName("AC-6: booking EXPIRED nhận PaymentSucceeded, ghế còn trống thì CONFIRMED, ghế SOLD đúng booking, không hoàn")
	void expiredBookingWithFreeSeatsIsConfirmed() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID booking = hold(customer, event, seats);
		expire(booking);
		UUID payment = succeededPayment(booking, 2_000_000);

		paymentSucceeded(payment, booking);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("CONFIRMED"));
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				booking)).isEqualTo(2);
		assertThat(events.of(BookingConfirmed.class)).hasSize(1);
		assertThat(events.of(BookingConfirmationFailed.class)).isEmpty();
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	@Test
	@DisplayName("Khách đã huỷ booking, thanh toán đến muộn, ghế còn trống: CONFIRMED")
	void cancelledBookingWithFreeSeatsIsConfirmed() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(customer, event, seats(event, 1));
		mockMvc.perform(delete("/api/v1/bookings/" + booking).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isNoContent());
		UUID payment = succeededPayment(booking, 1_000_000);

		paymentSucceeded(payment, booking);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("CONFIRMED"));
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				booking)).isEqualTo(1);
	}

	// ---- Không giữ lại được ghế: hoàn 100% ----

	@Test
	@DisplayName("AC-6: ghế đã bị người khác giữ thì hoàn 100% với khoá refund-{id}, booking giữ nguyên EXPIRED")
	void seatTakenByAnotherCustomerMeansAFullRefundAndTheBookingStaysExpired() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID booking = hold(customer, event, seats);
		expire(booking);
		clock.setInstant(AFTER_HOLD);
		UUID rival = hold(otherCustomer, event, seats.subList(0, 1));
		UUID payment = succeededPayment(booking, 2_000_000);

		paymentSucceeded(payment, booking);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		UUID refundId = jdbc.queryForObject("select id from payment.refunds", UUID.class);
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds"))
				.withHeader("Idempotency-Key", equalTo("refund-" + refundId))
				.withRequestBody(WireMock.containing("payment_intent=" + INTENT_ID))
				.withRequestBody(WireMock.containing("amount=2000000")));
		assertThat(jdbc.queryForMap("select amount_minor, reason, stripe_refund_id from payment.refunds"))
				.containsEntry("amount_minor", 2_000_000L).containsEntry("reason", Refund.REASON_BOOKING_CONFIRMATION_FAILED)
				.containsEntry("stripe_refund_id", "re_late_1");
		assertThat(statusOf(booking)).isEqualTo("EXPIRED");
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ?", booking)).isZero();
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'HELD'", rival))
				.isEqualTo(1);
		assertThat(events.of(BookingConfirmed.class)).isEmpty();
		assertThat(events.of(BookingConfirmationFailed.class).get(0).reason())
				.isEqualTo(BookingConfirmationFailed.SEATS_UNAVAILABLE);
	}

	@Test
	@DisplayName("Sự kiện không còn đang bán (SUSPENDED): hoàn 100%, booking giữ nguyên")
	void eventNoLongerOnSaleMeansAFullRefund() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(customer, event, seats(event, 1));
		expire(booking);
		jdbc.update("update booking.event_snapshots set status = 'SUSPENDED' where event_id = ?", event);
		UUID payment = succeededPayment(booking, 1_000_000);

		paymentSucceeded(payment, booking);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		assertThat(statusOf(booking)).isEqualTo("EXPIRED");
		assertThat(events.of(BookingConfirmationFailed.class).get(0).reason())
				.isEqualTo(BookingConfirmationFailed.EVENT_NOT_ON_SALE);
	}

	@Test
	@DisplayName("Stripe trả pending: bản ghi hoàn giữ PENDING kèm stripe_refund_id (webhook charge.refunded hoàn tất, xem RefundFlowTests)")
	void aPendingRefundStaysPendingWithItsStripeId() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("pending")));
		UUID event = onSaleEvent();
		UUID booking = hold(customer, event, seats(event, 1));
		expire(booking);
		jdbc.update("update booking.event_snapshots set status = 'ENDED' where event_id = ?", event);
		UUID payment = succeededPayment(booking, 1_000_000);

		paymentSucceeded(payment, booking);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(
				"select count(*) from payment.refunds where status = 'PENDING' and stripe_refund_id = 're_late_1'"))
				.isEqualTo(1));
	}

	// ---- Idempotency của hoàn tiền ----

	@Test
	@DisplayName("§8.3: cùng BookingConfirmationFailed hai lần thì chỉ hoàn một lần")
	void theSameFailureTwiceRefundsOnce() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(customer, event, seats(event, 1));
		expire(booking);
		jdbc.update("update booking.event_snapshots set status = 'SUSPENDED' where event_id = ?", event);
		UUID payment = succeededPayment(booking, 1_000_000);
		BookingConfirmationFailed message = new BookingConfirmationFailed(UUID.randomUUID(), NOW, booking, payment,
				BookingConfirmationFailed.EVENT_NOT_ON_SALE);

		for (int i = 0; i < 2; i++) {
			transaction.executeWithoutResult(status -> publisher.publishEvent(message));
		}

		await().atMost(Duration.ofSeconds(15)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds"))));
	}

	@Test
	@DisplayName("ADR 0026: Stripe lỗi rồi thử lại dùng đúng bản ghi và khoá refund-{id} cũ, không hoàn hai lần")
	void retryAfterAStripeFailureReusesTheSameRefundRecordAndKey() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(customer, event, seats(event, 1));
		expire(booking);
		UUID payment = succeededPayment(booking, 1_000_000);
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(aResponse().withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> refundService.refundFully(UUID.randomUUID(), payment, Refund.REASON_BOOKING_CONFIRMATION_FAILED))
				.isInstanceOf(RuntimeException.class);
		UUID refundId = jdbc.queryForObject("select id from payment.refunds", UUID.class);
		assertThat(jdbc.queryForObject("select status from payment.refunds", String.class)).isEqualTo("PENDING");

		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("succeeded")));
		refundService.refundFully(UUID.randomUUID(), payment, Refund.REASON_BOOKING_CONFIRMATION_FAILED);

		assertThat(count("select count(*) from payment.refunds")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from payment.refunds", String.class)).isEqualTo("SUCCEEDED");
		stripe.verify(2, postRequestedFor(urlEqualTo("/v1/refunds")).withHeader("Idempotency-Key",
				equalTo("refund-" + refundId)));
	}

	// ---- helpers ----

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder refund(String status) {
		return aResponse().withHeader("Content-Type", "application/json").withBody("""
				{"id":"re_late_1","object":"refund","amount":1000000,"currency":"vnd","status":"%s",
				 "payment_intent":"%s"}
				""".formatted(status, INTENT_ID));
	}

	private void expire(UUID booking) {
		clock.setInstant(AFTER_HOLD);
		assertThat(expiry.expireDueBookings()).isEqualTo(1);
		assertThat(statusOf(booking)).isEqualTo("EXPIRED");
		events.clear();
	}

	/** Payment đã thu tiền (SUCCEEDED) của booking, như sau khi webhook succeeded được xử lý. */
	private UUID succeededPayment(UUID booking, long amount) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, 'VND', 'SUCCEEDED', ?, ?)
				""", id, booking, INTENT_ID, amount, amount / 20, Timestamp.from(NOW), Timestamp.from(NOW));
		return id;
	}

	private void paymentSucceeded(UUID payment, UUID booking) {
		PaymentSucceeded message = new PaymentSucceeded(UUID.randomUUID(), clock.instant(), payment, booking, 0, "VND");
		transaction.executeWithoutResult(status -> publisher.publishEvent(message));
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

	private String statusOf(UUID booking) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, booking);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private UUID onSaleEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		return id;
	}

	private List<UUID> seats(UUID event, int count) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID seat = UUID.randomUUID();
			ids.add(seat);
			jdbc.update("""
					insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
					    seat_number, price_minor)
					values (?, ?, ?, 'VIP', 'A', 'A', ?, 1000000)
					""", event, seat, UUID.nameUUIDFromBytes("VIP".getBytes(StandardCharsets.UTF_8)), i + 1);
		}
		return ids;
	}

}
