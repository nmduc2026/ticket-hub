package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.payment.service.PaymentReconciliationService;

/**
 * Đối soát (plan Sprint 3 việc 6; AC-5; overview.md §7.3, §16.3; ADR 0019): PaymentIntent đã {@code succeeded} mà không
 * có webhook thì job đưa booking về {@code CONFIRMED}. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class PaymentReconciliationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant RECONCILE_TIME = NOW.plusSeconds(15 * 60);
	private static final String GAUGE = "tickethub.payment.succeeded_unsettled";

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
	PaymentReconciliationService reconciliation;

	@Autowired
	MeterRegistry metrics;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		events.clear();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/payment_intents")).willReturn(intent("pi_a", "requires_payment_method")));
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

	@Test
	@DisplayName("AC-5: PaymentIntent đã succeeded mà mất webhook thì đối soát đưa payment SUCCEEDED và booking CONFIRMED")
	void aLostSucceededWebhookIsRepairedByReconciliation() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = holdAndPay(event, "pi_a");
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_a")).willReturn(intent("pi_a", "succeeded")));
		clock.setInstant(RECONCILE_TIME);

		assertThat(reconciliation.reconcile()).isEqualTo(1);

		assertThat(paymentStatus(booking)).isEqualTo("SUCCEEDED");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("CONFIRMED"));
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				booking)).isEqualTo(1);
		assertThat(events.of(PaymentSucceeded.class)).hasSize(1);
		assertThat(events.of(BookingConfirmed.class)).hasSize(1);
	}

	@Test
	@DisplayName("Chạy đối soát lần hai không đổi gì (payment đã kết thúc không bị quét lại)")
	void reconciliationIsIdempotent() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = holdAndPay(event, "pi_a");
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_a")).willReturn(intent("pi_a", "succeeded")));
		clock.setInstant(RECONCILE_TIME);
		assertThat(reconciliation.reconcile()).isEqualTo(1);

		assertThat(reconciliation.reconcile()).isZero();

		stripe.verify(1, getRequestedFor(urlEqualTo("/v1/payment_intents/pi_a")));
		assertThat(events.of(PaymentSucceeded.class)).hasSize(1);
	}

	@Test
	@DisplayName("Payment mới hơn 15 phút thì chưa quét, không gọi Stripe")
	void youngPaymentsAreLeftAlone() throws Exception {
		UUID event = onSaleEvent();
		holdAndPay(event, "pi_a");
		clock.setInstant(RECONCILE_TIME.minusSeconds(1));

		assertThat(reconciliation.reconcile()).isZero();

		stripe.verify(0, getRequestedFor(WireMock.urlMatching("/v1/payment_intents/.*")));
	}

	@Test
	@DisplayName("PaymentIntent đã canceled ở Stripe: payment CANCELED; đang processing: payment PROCESSING; còn chờ khách: giữ nguyên")
	void otherStripeStatesAreMirroredOrLeftAlone() throws Exception {
		UUID event = onSaleEvent();
		UUID canceled = holdAndPay(event, "pi_canceled");
		UUID processing = holdAndPay(event, "pi_processing");
		UUID waiting = holdAndPay(event, "pi_waiting");
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_canceled")).willReturn(intent("pi_canceled", "canceled")));
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_processing"))
				.willReturn(intent("pi_processing", "processing")));
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_waiting"))
				.willReturn(intent("pi_waiting", "requires_payment_method")));
		clock.setInstant(RECONCILE_TIME);

		assertThat(reconciliation.reconcile()).isEqualTo(2);

		assertThat(paymentStatus(canceled)).isEqualTo("CANCELED");
		assertThat(paymentStatus(processing)).isEqualTo("PROCESSING");
		assertThat(paymentStatus(waiting)).isEqualTo("REQUIRES_PAYMENT");
	}

	@Test
	@DisplayName("Stripe lỗi với một payment không chặn các payment khác")
	void aStripeFailureDoesNotBlockTheOtherPayments() throws Exception {
		UUID event = onSaleEvent();
		UUID broken = holdAndPay(event, "pi_broken");
		UUID fine = holdAndPay(event, "pi_fine");
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_broken")).willReturn(aResponse().withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
		stripe.stubFor(get(urlEqualTo("/v1/payment_intents/pi_fine")).willReturn(intent("pi_fine", "succeeded")));
		clock.setInstant(RECONCILE_TIME);

		assertThat(reconciliation.reconcile()).isEqualTo(1);

		assertThat(paymentStatus(broken)).isEqualTo("REQUIRES_PAYMENT");
		assertThat(paymentStatus(fine)).isEqualTo("SUCCEEDED");
	}

	// ---- Chỉ số "đã thu tiền mà chưa có vé hay hoàn" ----

	@Test
	@DisplayName("§16.3: payment SUCCEEDED quá 5 phút mà booking chưa CONFIRMED và chưa hoàn thì tính vào chỉ số")
	void succeededButUnconfirmedPaymentsAreCounted() {
		UUID event = onSaleEvent();
		UUID unconfirmed = pendingBooking(event);
		insertPayment(unconfirmed, "pi_u", "SUCCEEDED", NOW);
		clock.setInstant(NOW.plusSeconds(301));

		reconciliation.reconcile();

		assertThat(metrics.get(GAUGE).gauge().value()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("§16.3: không tính khi booking đã CONFIRMED, đã có lần hoàn, hoặc chưa tới 5 phút")
	void confirmedRefundedOrRecentPaymentsAreNotCounted() {
		UUID event = onSaleEvent();
		UUID confirmed = pendingBooking(event);
		jdbc.update("update booking.bookings set status = 'CONFIRMED' where id = ?", confirmed);
		insertPayment(confirmed, "pi_c", "SUCCEEDED", NOW);
		UUID refunded = pendingBooking(event);
		UUID refundedPayment = insertPayment(refunded, "pi_r", "SUCCEEDED", NOW);
		jdbc.update("""
				insert into payment.refunds (id, payment_id, amount_minor, reason, status, created_at, updated_at)
				values (?, ?, 1000000, 'BOOKING_CONFIRMATION_FAILED', 'PENDING', ?, ?)
				""", UUID.randomUUID(), refundedPayment, Timestamp.from(NOW), Timestamp.from(NOW));
		UUID recent = pendingBooking(event);
		insertPayment(recent, "pi_n", "SUCCEEDED", NOW.plusSeconds(200));
		clock.setInstant(NOW.plusSeconds(301));

		reconciliation.reconcile();

		assertThat(metrics.get(GAUGE).gauge().value()).isZero();
	}

	// ---- helpers ----

	private static ResponseDefinitionBuilder intent(String id, String status) {
		return aResponse().withHeader("Content-Type", "application/json").withBody("""
				{"id":"%s","object":"payment_intent","amount":1000000,"currency":"vnd","status":"%s",
				 "client_secret":"placeholder-client-secret"}
				""".formatted(id, status));
	}

	/** Một khách mới giữ một ghế rồi bắt đầu thanh toán; Stripe trả PaymentIntent {@code intentId}. */
	private UUID holdAndPay(UUID event, String intentId) throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(intent(intentId, "requires_payment_method")));
		String user = TestTokens.token().subject(UUID.randomUUID()).bearer();
		String held = mockMvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, user)
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[\"%s\"]}".formatted(event, seat(event))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID booking = UUID.fromString(JsonPath.read(held, "$.bookingId"));
		mockMvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, user)
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":\"%s\"}".formatted(booking))).andExpect(status().isCreated());
		return booking;
	}

	private String statusOf(UUID booking) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, booking);
	}

	private String paymentStatus(UUID booking) {
		return jdbc.queryForObject("select status from payment.payments where booking_id = ?", String.class, booking);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private UUID insertPayment(UUID booking, String intentId, String status, Instant at) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, 1000000, 50000, 'VND', ?, ?, ?)
				""", id, booking, intentId, status, Timestamp.from(at), Timestamp.from(at));
		return id;
	}

	/** Booking ở trạng thái chờ thanh toán, không cần ghế (chỉ để Payment hỏi trạng thái). */
	private UUID pendingBooking(UUID event) {
		UUID booking = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, 'PENDING_PAYMENT', 1000000, ?, 'a@b.c')
				""", booking, UUID.randomUUID(), event, Timestamp.from(NOW.plusSeconds(600)));
		return booking;
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

	private UUID seat(UUID event) {
		UUID seat = UUID.randomUUID();
		jdbc.update("""
				insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
				    seat_number, price_minor)
				values (?, ?, ?, 'VIP', 'A', 'A', 1, 1000000)
				""", event, seat, UUID.nameUUIDFromBytes("VIP".getBytes(StandardCharsets.UTF_8)));
		return seat;
	}

}
