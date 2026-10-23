package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.service.BookingExpiryService;

/**
 * Bắt đầu thanh toán (plan Sprint 3 việc 2; BR-03, BR-06, BR-10; overview.md §7.2): hạn giữ ghế tối thiểu 90 giây,
 * ghi mốc ân hạn, PaymentIntent chỉ tạo một lần, phí làm tròn xuống. Stripe được giả lập bằng WireMock.
 */
@IntegrationTest
class PaymentApiTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String INTENT = """
			{"id":"pi_test_1","object":"payment_intent","amount":1000000,"currency":"vnd",
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
	BookingExpiryService expiry;

	private final UUID customer = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		stripe.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(INTENT)));
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		stripe.resetAll();
		jdbc.update("delete from payment.payments");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
	}

	// ---- BR-03: hạn giữ ghế tối thiểu 90 giây ----

	@Test
	@DisplayName("AC-1, BR-03: còn 89 giây thì 409 HOLD_EXPIRING, không gọi Stripe, không ghi mốc thanh toán")
	void lessThanNinetySecondsLeftIsRejected() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(89));

		pay(customer, booking, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRING"));

		assertThat(paymentStartedAt(booking)).isNull();
		assertThat(count("select count(*) from payment.payments")).isZero();
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/payment_intents")));
	}

	@Test
	@DisplayName("BR-03: còn đúng 90 giây thì được thanh toán")
	void exactlyNinetySecondsLeftIsAccepted() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(90));

		pay(customer, booking, key()).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("AC-1: còn ≥ 90 giây trả clientSecret, ghi payment_started_at, lưu phí; Stripe nhận số tiền VND nguyên")
	void startsPaymentAndRecordsTheStartAndTheFee() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));

		pay(customer, booking, key()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.clientSecret").value("placeholder-client-secret"))
				.andExpect(jsonPath("$.paymentId").isNotEmpty()).andExpect(jsonPath("$.amountMinor").value(1_000_000))
				.andExpect(jsonPath("$.platformFeeMinor").value(50_000)).andExpect(jsonPath("$.currency").value("VND"));

		assertThat(paymentStartedAt(booking)).isEqualTo(NOW);
		assertThat(jdbc.queryForMap("select amount_minor, application_fee_minor, currency, status, "
				+ "stripe_payment_intent_id from payment.payments where booking_id = ?", booking))
				.containsEntry("amount_minor", 1_000_000L).containsEntry("application_fee_minor", 50_000L)
				.containsEntry("currency", "VND").containsEntry("status", "REQUIRES_PAYMENT")
				.containsEntry("stripe_payment_intent_id", "pi_test_1");
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents"))
				.withHeader("Idempotency-Key", equalTo("booking-" + booking))
				.withRequestBody(containing("amount=1000000")).withRequestBody(containing("currency=vnd"))
				.withRequestBody(containing("metadata[bookingId]=" + booking)));
	}

	@Test
	@DisplayName("BR-03: đã bắt đầu thanh toán thì không hết hạn trong 5 phút ân hạn, hết ân hạn thì EXPIRED")
	void aBookingThatStartedPaymentIsNotExpiredDuringTheGracePeriod() throws Exception {
		Instant holdEnds = NOW.plusSeconds(300);
		UUID booking = pendingBooking(customer, 1_000_000, holdEnds);
		pay(customer, booking, key()).andExpect(status().isCreated());

		clock.setInstant(holdEnds.plusSeconds(299));
		assertThat(expiry.expireDueBookings()).isZero();
		assertThat(statusOf(booking)).isEqualTo("PENDING_PAYMENT");

		clock.setInstant(holdEnds.plusSeconds(300));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);
		assertThat(statusOf(booking)).isEqualTo("EXPIRED");
	}

	@Test
	@DisplayName("Đã bắt đầu thanh toán nhưng qua cả ân hạn: 409 HOLD_EXPIRING")
	void startingAgainAfterTheGracePeriodIsRejected() throws Exception {
		Instant holdEnds = NOW.plusSeconds(300);
		UUID booking = pendingBooking(customer, 1_000_000, holdEnds);
		pay(customer, booking, key()).andExpect(status().isCreated());

		clock.setInstant(holdEnds.plusSeconds(300));
		pay(customer, booking, key()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRING"));
	}

	// ---- BR-06, BR-10: phí ----

	@Test
	@DisplayName("AC-8, BR-06: 333.333 VND × 500 bps = 16.666 (làm tròn xuống)")
	void feeIsRoundedDown() throws Exception {
		UUID booking = pendingBooking(customer, 333_333, NOW.plusSeconds(300));

		pay(customer, booking, key()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.amountMinor").value(333_333)).andExpect(jsonPath("$.platformFeeMinor").value(16_666));

		assertThat(count("select count(*) from payment.payments where application_fee_minor = 16666")).isEqualTo(1);
	}

	// ---- Idempotency ----

	@Test
	@DisplayName("AC-1, §6.7: gửi lại cùng Idempotency-Key trả cùng clientSecret, PaymentIntent chỉ tạo một lần")
	void sameKeyReturnsTheSameResponseAndCreatesTheIntentOnce() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));
		String key = key();

		String first = pay(customer, booking, key).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String second = pay(customer, booking, key).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();

		assertThat(second).isEqualTo(first);
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/payment_intents")));
		assertThat(count("select count(*) from payment.payments")).isEqualTo(1);
	}

	@Test
	@DisplayName("Khác Idempotency-Key cho cùng booking: vẫn một payment, Stripe nhận cùng khoá booking-{id}")
	void anotherKeyForTheSameBookingStillYieldsOnePayment() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));

		String first = pay(customer, booking, key()).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String second = pay(customer, booking, key()).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(count("select count(*) from payment.payments")).isEqualTo(1);
		stripe.verify(2, postRequestedFor(urlEqualTo("/v1/payment_intents"))
				.withHeader("Idempotency-Key", equalTo("booking-" + booking)));
	}

	@Test
	@DisplayName("Nhiều request cùng lúc với khoá khác nhau chỉ tạo đúng một payment")
	void concurrentRequestsCreateExactlyOnePayment() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));
		int threads = 10;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					go.await();
					return pay(customer, booking, key()).andExpect(status().isCreated()).andReturn().getResponse()
							.getContentAsString();
				}));
			}
			go.countDown();
			List<String> bodies = new ArrayList<>();
			for (Future<String> future : futures) {
				bodies.add(future.get(60, TimeUnit.SECONDS));
			}
			assertThat(bodies).allSatisfy(body -> assertThat(body).isEqualTo(bodies.get(0)));
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(count("select count(*) from payment.payments")).isEqualTo(1);
	}

	// ---- Quyền và trạng thái ----

	@Test
	@DisplayName("Booking của người khác hoặc không tồn tại: 404 BOOKING_NOT_FOUND, không đụng tới booking")
	void someoneElsesOrUnknownBookingIs404() throws Exception {
		UUID booking = pendingBooking(UUID.randomUUID(), 1_000_000, NOW.plusSeconds(300));

		pay(customer, booking, key()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
		pay(customer, UUID.randomUUID(), key()).andExpect(status().isNotFound());

		assertThat(paymentStartedAt(booking)).isNull();
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/payment_intents")));
	}

	@Test
	@DisplayName("Booking đã hết hạn, đã huỷ hoặc đã xác nhận: 409 BOOKING_NOT_PAYABLE")
	void bookingThatIsNotPendingCannotBePaid() throws Exception {
		for (String status : List.of("EXPIRED", "CANCELLED", "CONFIRMED")) {
			UUID booking = bookingWithStatus(customer, status, 1_000_000, NOW.plusSeconds(300));

			pay(customer, booking, key()).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BOOKING_NOT_PAYABLE"));
		}
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/payment_intents")));
	}

	@Test
	void withoutLoginIs401AndWithoutTheCustomerRoleIs403() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));
		String body = "{\"bookingId\":\"%s\"}".formatted(booking);

		mockMvc.perform(post("/api/v1/payments").header("Idempotency-Key", key())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).roles("ORGANIZER").bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void idempotencyKeyIsRequired() throws Exception {
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));

		mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":\"%s\"}".formatted(booking)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	@DisplayName("Thiếu bookingId: 400 VALIDATION_FAILED")
	void bookingIdIsRequired() throws Exception {
		mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	// ---- Stripe lỗi ----

	@Test
	@DisplayName("Stripe lỗi: 503 PAYMENT_PROVIDER_UNAVAILABLE, không ghi payment; thử lại khi Stripe tốt lên thì được")
	void stripeFailureIs503AndNothingIsRecorded() throws Exception {
		stripe.resetAll();
		stripe.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")
						.withBody("{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
		UUID booking = pendingBooking(customer, 1_000_000, NOW.plusSeconds(300));

		pay(customer, booking, key()).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
		assertThat(count("select count(*) from payment.payments")).isZero();

		stripe.resetAll();
		stripe.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/v1/payment_intents"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(INTENT)));
		pay(customer, booking, key()).andExpect(status().isCreated());
		assertThat(count("select count(*) from payment.payments")).isEqualTo(1);
	}

	// ---- helpers ----

	private static String key() {
		return UUID.randomUUID().toString();
	}

	private ResultActions pay(UUID user, UUID booking, String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/v1/payments")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":\"%s\"}".formatted(booking)));
	}

	private Instant paymentStartedAt(UUID booking) {
		Timestamp timestamp = jdbc.queryForObject("select payment_started_at from booking.bookings where id = ?",
				Timestamp.class, booking);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private String statusOf(UUID booking) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, booking);
	}

	private int count(String sql) {
		Integer value = jdbc.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

	private UUID pendingBooking(UUID user, long totalMinor, Instant expiresAt) {
		return bookingWithStatus(user, "PENDING_PAYMENT", totalMinor, expiresAt);
	}

	/** Một booking và bản sao sự kiện (phí 500 bps) của nó; ghế không cần cho bước bắt đầu thanh toán. */
	private UUID bookingWithStatus(UUID user, String status, long totalMinor, Instant expiresAt) {
		UUID event = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", event, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		UUID booking = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, ?, ?, 'a@b.c')
				""", booking, user, event, status, totalMinor, Timestamp.from(expiresAt));
		return booking;
	}

}
