package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.RefundRequested;
import com.ndz.payment.service.PaymentRefundService;

/**
 * Destination charge (plan Sprint 6 việc 2; AC-3, AC-4; BR-07, BR-08, BR-10; overview.md §7.8; ADR 0022, 0023, 0026):
 * PaymentIntent gửi {@code application_fee_amount} + {@code transfer_data.destination} khi organizer có connected
 * account; hoàn kéo lại phần đã chuyển ({@code reverse_transfer}) và hoàn phí khi không phải khách tự yêu cầu
 * ({@code refund_application_fee}); organizer dữ liệu seed (chưa có Connect) vẫn thu / hoàn trên platform, không có
 * các tham số này. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class DestinationChargeTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String CONNECTED_ACCOUNT = "acct_connected_1";
	private static final String INTENT = """
			{"id":"pi_connect_1","object":"payment_intent","amount":1000000,"currency":"vnd",
			 "status":"requires_payment_method","client_secret":"placeholder-client-secret"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	WireMockServer stripe;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	PaymentRefundService refunds;

	@Autowired
	TransactionTemplate transaction;

	private final UUID customer = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(INTENT)));
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		stripe.resetAll();
		jdbc.update("delete from payment.refunds");
		jdbc.update("delete from payment.refund_queue");
		jdbc.update("delete from payment.payments");
		jdbc.update("delete from payment.processed_events");
		jdbc.update("delete from payment.connected_accounts");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
	}

	// ---- AC-3: tạo PaymentIntent ----

	@Test
	@DisplayName("AC-3: organizer có connected account chargesEnabled → PaymentIntent có transfer_data.destination và "
			+ "application_fee_amount = 50.000 (phí 5% của 1.000.000đ)")
	void connectedOrganizerGetsADestinationCharge() throws Exception {
		UUID organizer = connectedOrganizer(true);
		UUID booking = pendingBooking(organizer, 1_000_000);

		pay(booking).andExpect(status().isCreated());

		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents"))
				.withRequestBody(containing("application_fee_amount=50000"))
				.withRequestBody(containing("transfer_data[destination]=" + CONNECTED_ACCOUNT)));
		assertThat(jdbc.queryForObject("select destination_account_id from payment.payments where booking_id = ?",
				String.class, booking)).isEqualTo(CONNECTED_ACCOUNT);
	}

	@Test
	@DisplayName("AC-3: organizer có connected account nhưng chưa chargesEnabled → 409 ORGANIZER_PAYOUT_NOT_READY, "
			+ "không gọi Stripe")
	void organizerWithoutChargesEnabledIsRejected() throws Exception {
		UUID organizer = connectedOrganizer(false);
		UUID booking = pendingBooking(organizer, 1_000_000);

		pay(booking).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORGANIZER_PAYOUT_NOT_READY"));

		assertThat(count("select count(*) from payment.payments")).isZero();
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/payment_intents")));
	}

	@Test
	@DisplayName("AC-3: organizer dữ liệu seed (chưa có connected account) vẫn thu trên platform: không có "
			+ "application_fee_amount / transfer_data, destination_account_id NULL")
	void seedOrganizerWithoutAConnectedAccountUsesThePlatformCharge() throws Exception {
		UUID booking = pendingBooking(TestTokens.ORGANIZER_ID, 1_000_000);

		pay(booking).andExpect(status().isCreated());

		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents"))
				.withRequestBody(notMatching(".*application_fee_amount.*"))
				.withRequestBody(notMatching(".*transfer_data.*")));
		assertThat(jdbc.queryForObject("select destination_account_id from payment.payments where booking_id = ?",
				String.class, booking)).isNull();
	}

	// ---- AC-4: hoàn ----

	@Test
	@DisplayName("AC-4 (BR-07): hoàn theo yêu cầu khách của destination charge → reverse_transfer, không "
			+ "refund_application_fee, số tiền trừ phí (950.000đ)")
	void customerRefundOfADestinationChargeReversesTheTransferWithoutTheFee() {
		UUID organizer = connectedOrganizer(true);
		UUID booking = UUID.randomUUID();
		UUID payment = destinationPayment(booking, 1_000_000, 50_000, CONNECTED_ACCOUNT);
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refundResponse()));

		refund(booking, organizer, RefundRequested.CUSTOMER_REQUEST, 950_000);

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(count("select count(*) from payment.refunds where payment_id = '"
						+ payment + "' and status = 'SUCCEEDED'")).isEqualTo(1));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=950000"))
				.withRequestBody(containing("reverse_transfer=true"))
				.withRequestBody(notMatching(".*refund_application_fee.*")));
	}

	@Test
	@DisplayName("AC-4 (BR-08): hoàn vì huỷ sự kiện của destination charge → reverse_transfer và "
			+ "refund_application_fee, hoàn 100% (1.000.000đ)")
	void eventCancelledRefundOfADestinationChargeReversesTheTransferAndTheFee() {
		UUID organizer = connectedOrganizer(true);
		UUID booking = UUID.randomUUID();
		UUID payment = destinationPayment(booking, 1_000_000, 50_000, CONNECTED_ACCOUNT);
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refundResponse()));

		refund(booking, organizer, RefundRequested.EVENT_CANCELLED, 1_000_000);
		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(count("select count(*) from payment.refund_queue")).isEqualTo(1));
		refunds.processRefundQueue();

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(count("select count(*) from payment.refunds where payment_id = '"
						+ payment + "' and status = 'SUCCEEDED'")).isEqualTo(1));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=1000000"))
				.withRequestBody(containing("reverse_transfer=true"))
				.withRequestBody(containing("refund_application_fee=true")));
	}

	@Test
	@DisplayName("AC-4: payment thu trên platform (seed, destination_account_id NULL) huỷ sự kiện → hoàn 100% không "
			+ "có reverse_transfer / refund_application_fee")
	void eventCancelledRefundOfAPlatformChargeHasNoTransferParams() {
		UUID booking = UUID.randomUUID();
		UUID payment = destinationPayment(booking, 1_000_000, 50_000, null);
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refundResponse()));

		refund(booking, TestTokens.ORGANIZER_ID, RefundRequested.EVENT_CANCELLED, 1_000_000);
		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(count("select count(*) from payment.refund_queue")).isEqualTo(1));
		refunds.processRefundQueue();

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(count("select count(*) from payment.refunds where payment_id = '"
						+ payment + "' and status = 'SUCCEEDED'")).isEqualTo(1));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=1000000"))
				.withRequestBody(notMatching(".*reverse_transfer.*"))
				.withRequestBody(notMatching(".*refund_application_fee.*")));
	}

	// ---- helpers ----

	/** Organizer với connected account (ADR 0022); {@code chargesEnabled} quyết định có thu được không (§7.8). */
	private UUID connectedOrganizer(boolean chargesEnabled) {
		UUID organizer = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO payment.connected_accounts (organizer_id, stripe_account_id, charges_enabled,
				    payouts_enabled, details_submitted, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", organizer, CONNECTED_ACCOUNT, chargesEnabled, chargesEnabled, chargesEnabled,
				Timestamp.from(NOW), Timestamp.from(NOW));
		return organizer;
	}

	private UUID pendingBooking(UUID organizer, long totalMinor) {
		UUID event = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", event, organizer, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		UUID booking = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, 'PENDING_PAYMENT', ?, ?, 'a@b.c')
				""", booking, customer, event, totalMinor, Timestamp.from(NOW.plusSeconds(300)));
		return booking;
	}

	private org.springframework.test.web.servlet.ResultActions pay(UUID booking) throws Exception {
		return mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":\"%s\"}".formatted(booking)));
	}

	/** Payment {@code SUCCEEDED} đã thu xong, sẵn để hoàn; {@code destinationAccountId} null = thu trên platform. */
	private UUID destinationPayment(UUID booking, long amount, long fee, String destinationAccountId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, destination_account_id, currency, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, 'VND', 'SUCCEEDED', ?, ?)
				""", id, booking, "pi_" + booking, amount, fee, destinationAccountId, Timestamp.from(NOW),
				Timestamp.from(NOW));
		return id;
	}

	private void refund(UUID booking, UUID organizer, String reason, long amountMinor) {
		RefundRequested message = new RefundRequested(UUID.randomUUID(), NOW, booking, customer, UUID.randomUUID(),
				"Sự kiện thử", "a@b.c", reason, amountMinor, "VND", organizer);
		transaction.executeWithoutResult(status -> publisher.publishEvent(message));
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder refundResponse() {
		return aResponse().withHeader("Content-Type", "application/json").withBody(
				"{\"id\":\"re_1\",\"object\":\"refund\",\"status\":\"succeeded\",\"payment_intent\":\"pi\"}");
	}

	private int count(String sql) {
		Integer value = jdbc.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

}
