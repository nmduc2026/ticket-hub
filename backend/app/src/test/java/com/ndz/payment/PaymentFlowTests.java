package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import com.ndz.StripeStubConfiguration;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.service.BookingExpiryService;

/**
 * Luồng thanh toán đi qua các module (plan Sprint 3 việc 4; overview.md §7.2, §7.3, §7.7; ADR 0019): giữ ghế → trả
 * tiền → webhook → booking {@code CONFIRMED}, ghế {@code SOLD}; booking hết hạn hoặc bị huỷ → PaymentIntent bị huỷ.
 * Listener chạy bất đồng bộ nên chờ bằng Awaitility. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class PaymentFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String INTENT_ID = "pi_flow_1";
	private static final String CANCEL_URL = "/v1/payment_intents/" + INTENT_ID + "/cancel";
	private static final String RETRIEVE_URL = "/v1/payment_intents/" + INTENT_ID;

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
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	private final UUID customer = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		events.clear();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(intent("requires_payment_method")));
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		stripe.resetAll();
		jdbc.update("delete from payment.processed_events");
		jdbc.update("delete from payment.stripe_events");
		jdbc.update("delete from payment.payments");
		jdbc.update("delete from booking.processed_events");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
	}

	// ---- Thanh toán thành công ----

	@Test
	@DisplayName("AC-2: giữ ghế → trả tiền → webhook succeeded: booking CONFIRMED, mọi ghế SOLD, phát BookingConfirmed đủ thông tin")
	void paidBookingIsConfirmedAndItsSeatsAreSold() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID booking = hold(event, seats);
		pay(booking);

		sendWebhook("evt_ok", "payment_intent.succeeded", "succeeded");

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("CONFIRMED"));
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				booking)).isEqualTo(2);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isZero();
		assertThat(jdbc.queryForObject("select status from payment.payments where booking_id = ?", String.class,
				booking)).isEqualTo("SUCCEEDED");

		List<BookingConfirmed> confirmed = events.of(BookingConfirmed.class);
		assertThat(confirmed).hasSize(1);
		BookingConfirmed message = confirmed.get(0);
		assertThat(message.bookingId()).isEqualTo(booking);
		assertThat(message.userId()).isEqualTo(customer);
		assertThat(message.eventTitle()).isEqualTo("Sự kiện thử");
		assertThat(message.venueName()).isEqualTo("Sài Gòn Arena");
		assertThat(message.startsAt()).isEqualTo(NOW.plusSeconds(30 * 86_400));
		assertThat(message.contactEmail()).isEqualTo("customer@tickethub.local");
		assertThat(message.totalMinor()).isEqualTo(2_000_000);
		assertThat(message.currency()).isEqualTo("VND");
		assertThat(message.seats().stream().map(BookingConfirmed.Seat::seatId).collect(Collectors.toSet()))
				.isEqualTo(Set.copyOf(seats));
	}

	@Test
	@DisplayName("§8.3: nhận cùng PaymentSucceeded hai lần thì chỉ xác nhận một lần")
	void theSamePaymentSucceededTwiceConfirmsOnce() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));
		pay(booking);
		UUID paymentId = jdbc.queryForObject("select id from payment.payments where booking_id = ?", UUID.class,
				booking);
		PaymentSucceeded message = new PaymentSucceeded(UUID.randomUUID(), NOW, paymentId, booking, 1_000_000, "VND");

		for (int i = 0; i < 2; i++) {
			transaction.executeWithoutResult(status -> publisher.publishEvent(message));
		}

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("CONFIRMED"));
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> assertThat(events.of(BookingConfirmed.class)).hasSize(1));
		assertThat(count("select count(*) from booking.processed_events where consumer = ?",
				"booking.payment-succeeded")).isEqualTo(1);
	}

	// ---- Booking hết hạn hoặc bị huỷ thì huỷ PaymentIntent ----

	@Test
	@DisplayName("§7.3: booking hết hạn (kể cả ân hạn) thì PaymentIntent bị huỷ, payment CANCELED")
	void expiredBookingCancelsThePaymentIntent() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo(CANCEL_URL)).willReturn(intent("canceled")));
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));
		pay(booking);

		clock.setInstant(NOW.plusSeconds(600 + 300));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(paymentStatus(booking))
				.isEqualTo("CANCELED"));
		stripe.verify(1, postRequestedFor(urlEqualTo(CANCEL_URL)));
		assertThat(statusOf(booking)).isEqualTo("EXPIRED");
	}

	@Test
	@DisplayName("AC-7, BR-12: khách huỷ booking đã tạo PaymentIntent thì PaymentIntent bị huỷ")
	void cancellingTheBookingCancelsThePaymentIntent() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo(CANCEL_URL)).willReturn(intent("canceled")));
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));
		pay(booking);

		mockMvc.perform(delete("/api/v1/bookings/" + booking).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(paymentStatus(booking))
				.isEqualTo("CANCELED"));
		stripe.verify(1, postRequestedFor(urlEqualTo(CANCEL_URL)));
	}

	@Test
	@DisplayName("Booking huỷ khi chưa bắt đầu thanh toán: không gọi Stripe")
	void cancellingABookingWithoutAPaymentDoesNotCallStripe() throws Exception {
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));

		mockMvc.perform(delete("/api/v1/bookings/" + booking).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isNoContent());

		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(
				() -> stripe.verify(0, postRequestedFor(WireMock.urlMatching("/v1/payment_intents/.*/cancel"))));
	}

	@Test
	@DisplayName("ADR 0019: huỷ thất bại vì PaymentIntent đã succeeded thì lấy lại từ Stripe và xử lý như PaymentSucceeded")
	void cancelFailingBecauseTheIntentAlreadySucceededIsTreatedAsPaid() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo(CANCEL_URL)).willReturn(aResponse().withStatus(400)
				.withHeader("Content-Type", "application/json").withBody("""
						{"error":{"type":"invalid_request_error","code":"payment_intent_unexpected_state",
						 "message":"You cannot cancel this PaymentIntent because it has a status of succeeded."}}
						""")));
		stripe.stubFor(get(urlEqualTo(RETRIEVE_URL)).willReturn(intent("succeeded")));
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));
		pay(booking);

		mockMvc.perform(delete("/api/v1/bookings/" + booking).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(paymentStatus(booking))
				.isEqualTo("SUCCEEDED"));
		assertThat(events.of(PaymentSucceeded.class)).hasSize(1);
		assertThat(events.of(PaymentSucceeded.class).get(0).bookingId()).isEqualTo(booking);
	}

	@Test
	@DisplayName("Stripe lỗi khi huỷ: payment không bị đánh dấu CANCELED (event được thử lại sau, đối soát là lưới cuối)")
	void stripeFailureWhileCancellingLeavesThePaymentOpen() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo(CANCEL_URL)).willReturn(aResponse().withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
		UUID event = onSaleEvent();
		UUID booking = hold(event, seats(event, 1));
		pay(booking);

		mockMvc.perform(delete("/api/v1/bookings/" + booking).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(15))
				.untilAsserted(() -> stripe.verify(1, postRequestedFor(urlEqualTo(CANCEL_URL))));
		assertThat(paymentStatus(booking)).isEqualTo("REQUIRES_PAYMENT");
	}

	// ---- helpers ----

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder intent(String status) {
		return aResponse().withHeader("Content-Type", "application/json")
				.withBody("""
						{"id":"%s","object":"payment_intent","amount":1000000,"currency":"vnd","status":"%s",
						 "client_secret":"placeholder-client-secret"}
						""".formatted(INTENT_ID, status));
	}

	private UUID hold(UUID event, List<UUID> seats) throws Exception {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		String body = mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.bookingId"));
	}

	private void pay(UUID booking) throws Exception {
		mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":\"%s\"}".formatted(booking))).andExpect(status().isCreated());
	}

	private void sendWebhook(String eventId, String type, String intentStatus) throws Exception {
		String payload = """
				{"id":"%s","object":"event","api_version":"2025-01-27.acacia","created":%d,"livemode":false,
				 "type":"%s","data":{"object":{"id":"%s","object":"payment_intent","amount":1000000,
				 "currency":"vnd","status":"%s"}}}
				""".formatted(eventId, NOW.getEpochSecond(), type, INTENT_ID, intentStatus);
		long timestamp = NOW.getEpochSecond();
		mockMvc.perform(post("/api/v1/webhooks/stripe").header("Stripe-Signature", signature(payload, timestamp))
				.contentType(MediaType.APPLICATION_JSON).content(payload.getBytes(StandardCharsets.UTF_8)))
				.andExpect(status().isOk());
	}

	private static String signature(String payload, long timestamp) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(StripeStubConfiguration.WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"));
		return "t=" + timestamp + ",v1="
				+ HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
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
