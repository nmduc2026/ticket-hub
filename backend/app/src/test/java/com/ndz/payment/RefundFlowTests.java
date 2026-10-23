package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.MailpitApi;
import com.ndz.MutableClock;
import com.ndz.StripeStubConfiguration;
import com.ndz.TestTokens;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.payment.service.PaymentRefundService;

/**
 * Hoàn vé phía Payment (plan Sprint 4 việc 2; AC-1, AC-4; BR-07, BR-10; overview.md §7.4, §7.7; ADR 0022, 0026): số
 * tiền trừ phí nền tảng, khoá {@code refund-{refundId}}, webhook {@code charge.refunded} / {@code refund.failed},
 * booking sang {@code REFUNDED} / {@code REFUND_FAILED}, email xác nhận. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class RefundFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String INTENT_ID = "pi_refund_1";
	private static final String WEBHOOK = "/api/v1/webhooks/stripe";

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
	MailpitApi mailpit;

	@Autowired
	PaymentRefundService refundService;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	private final UUID customer = UUID.randomUUID();
	private final String email = "refund-" + UUID.randomUUID() + "@tickethub.local";
	private UUID eventId;
	private UUID bookingId;
	private UUID paymentId;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		events.clear();
		mailpit.clear();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("succeeded")));
		eventId = event();
		bookingId = confirmedBooking();
		paymentId = succeededPayment(bookingId, 1_000_000, 50_000);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		stripe.resetAll();
		jdbc.update("delete from payment.processed_events");
		jdbc.update("delete from payment.refunds");
		jdbc.update("delete from payment.stripe_events");
		jdbc.update("delete from payment.payments");
		jdbc.update("delete from ticket.tickets");
		jdbc.update("delete from ticket.revoked_bookings");
		jdbc.update("delete from ticket.processed_events");
		jdbc.update("delete from booking.revoked_tickets");
		jdbc.update("delete from booking.processed_events");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
		jdbc.update("delete from notification.processed_events");
		jdbc.update("delete from notification.notification_log");
	}

	// ---- AC-1 ----

	@Test
	@DisplayName("AC-1 (BR-07): vé 1.000.000đ phí 5% → Stripe nhận lệnh hoàn 950.000đ, booking REFUNDED, có email xác nhận")
	void customerRefundSendsNinetyFiveThousandToStripeAndEndsRefunded() throws Exception {
		requestRefund().andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(statusOf(bookingId)).isEqualTo("REFUNDED"));
		UUID refundId = jdbc.queryForObject("select id from payment.refunds", UUID.class);
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds"))
				.withHeader("Idempotency-Key", equalTo("refund-" + refundId))
				.withRequestBody(containing("payment_intent=" + INTENT_ID))
				.withRequestBody(containing("amount=950000")));
		assertThat(jdbc.queryForMap("select amount_minor, status, reason, booking_id from payment.refunds"))
				.containsEntry("amount_minor", 950_000L).containsEntry("status", "SUCCEEDED")
				.containsEntry("reason", RefundRequested.CUSTOMER_REQUEST).containsEntry("booking_id", bookingId);
		assertThat(events.of(RefundCompleted.class)).hasSize(1);
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mailpit.countTo(email)).isEqualTo(1));
		assertThat(mailpit.subjectsTo(email)).containsExactly("Đã hoàn tiền cho Sự kiện thử");
		assertThat(mailpit.latestHtmlTo(email)).contains("950.000");
	}

	@Test
	@DisplayName("BR-08/§7.3: lý do phát vé lỗi (TICKET_ISSUANCE_FAILED) hoàn 100% = 1.000.000đ")
	void ticketIssuanceFailureRefundsTheWholeAmountIncludingTheFee() {
		publishRefundRequested(RefundRequested.TICKET_ISSUANCE_FAILED, 1_000_000);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=1000000")));
	}

	@Test
	@DisplayName("Payment tự tính số tiền: event báo sai số thì vẫn hoàn theo payment (950.000đ)")
	void paymentComputesTheAmountItselfInsteadOfTrustingTheEvent() {
		publishRefundRequested(RefundRequested.CUSTOMER_REQUEST, 1_000_000);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=950000")));
	}

	// ---- Webhook ----

	@Test
	@DisplayName("Stripe trả pending: booking vẫn REFUND_REQUESTED tới khi webhook charge.refunded đến, rồi REFUNDED")
	void aPendingRefundCompletesOnTheChargeRefundedWebhook() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("pending")));
		requestRefund().andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where stripe_refund_id = 're_1'")).isEqualTo(1));
		assertThat(statusOf(bookingId)).isEqualTo("REFUND_REQUESTED");

		webhook("evt_charge_refunded", "charge.refunded", charge()).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(statusOf(bookingId)).isEqualTo("REFUNDED"));
		assertThat(jdbc.queryForObject("select status from payment.refunds", String.class)).isEqualTo("SUCCEEDED");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mailpit.countTo(email)).isEqualTo(1));
	}

	@Test
	@DisplayName("AC-6: charge.refunded gửi lại hoặc đến khi lần hoàn đã xong thì bị bỏ qua, không phát RefundCompleted thêm")
	void aSecondChargeRefundedWebhookChangesNothing() throws Exception {
		requestRefund().andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(statusOf(bookingId)).isEqualTo("REFUNDED"));

		webhook("evt_late_1", "charge.refunded", charge()).andExpect(status().isOk());
		webhook("evt_late_1", "charge.refunded", charge()).andExpect(status().isOk()); // cùng event id

		assertThat(events.of(RefundCompleted.class)).hasSize(1);
		assertThat(statusOf(bookingId)).isEqualTo("REFUNDED");
	}

	// ---- AC-4: hoàn thất bại ----

	@Test
	@DisplayName("AC-4: Stripe báo refund.failed → REFUND_FAILED, vé vẫn REVOKED, ghi refund_failed_at")
	void aFailedRefundLeavesTheBookingRefundFailedWithTicketsRevoked() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("pending")));
		issueTicket();
		requestRefund().andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where stripe_refund_id = 're_1'")).isEqualTo(1));

		webhook("evt_refund_failed", "refund.failed", failedRefund("expired_or_canceled_card"))
				.andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(statusOf(bookingId)).isEqualTo("REFUND_FAILED"));
		assertThat(jdbc.queryForMap("select status, failure_reason from payment.refunds"))
				.containsEntry("status", "FAILED").containsEntry("failure_reason", "expired_or_canceled_card");
		assertThat(jdbc.queryForList("select status from ticket.tickets where booking_id = ?", String.class, bookingId))
				.containsOnly("REVOKED");
		assertThat(jdbc.queryForObject("select refund_failed_at from booking.bookings where id = ?", Timestamp.class,
				bookingId)).isNotNull();
		assertThat(events.of(RefundFailed.class)).hasSize(1);
		assertThat(mailpit.countTo(email)).isZero();
	}

	@Test
	@DisplayName("Stripe từ chối ngay khi tạo (lỗi có mã) → REFUND_FAILED, không thử lại vô hạn")
	void stripeRejectingTheRefundOutrightFailsItWithoutRetrying() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(aResponse().withStatus(400)
				.withHeader("Content-Type", "application/json").withBody("""
						{"error":{"type":"invalid_request_error","code":"charge_already_refunded",
						 "message":"Charge has already been refunded."}}
						""")));

		requestRefund().andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(statusOf(bookingId)).isEqualTo("REFUND_FAILED"));
		assertThat(jdbc.queryForObject("select failure_reason from payment.refunds", String.class))
				.isEqualTo("charge_already_refunded");
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	@Test
	@DisplayName("Lỗi tạm thời (Stripe 500): ném lỗi để event được thử lại; booking chưa đổi, lần hoàn giữ PENDING")
	void aTransientStripeErrorKeepsTheRefundPendingForRetry() {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(aResponse().withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
		jdbc.update("update booking.bookings set status = 'REFUND_REQUESTED' where id = ?", bookingId);
		RefundRequested message = refundRequested(RefundRequested.CUSTOMER_REQUEST, 950_000);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> refundService.refundForBooking(message))
				.isInstanceOf(RuntimeException.class);

		assertThat(jdbc.queryForObject("select status from payment.refunds", String.class)).isEqualTo("PENDING");
		assertThat(statusOf(bookingId)).isEqualTo("REFUND_REQUESTED");
	}

	@Test
	@DisplayName("Payment không hoàn được (chưa SUCCEEDED) → RefundFailed để booking không kẹt REFUND_REQUESTED")
	void aPaymentThatCannotBeRefundedFailsTheRefund() throws Exception {
		jdbc.update("update payment.payments set status = 'CANCELED' where id = ?", paymentId);

		requestRefund().andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(statusOf(bookingId)).isEqualTo("REFUND_FAILED"));
		assertThat(count("select count(*) from payment.refunds")).isZero();
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	@Test
	@DisplayName("§8.3: cùng RefundRequested hai lần chỉ hoàn một lần")
	void theSameRefundRequestedTwiceRefundsOnce() {
		RefundRequested message = refundRequested(RefundRequested.CUSTOMER_REQUEST, 950_000);

		for (int i = 0; i < 2; i++) {
			transaction.executeWithoutResult(status -> publisher.publishEvent(message));
		}

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where status = 'SUCCEEDED'")).isEqualTo(1));
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds"))));
	}

	// ---- Admin hoàn thủ công (việc 3) ----

	@Test
	@DisplayName("AC-4: Admin hoàn thủ công → bản ghi refunds mới với khoá mới → REFUNDED; vé vẫn REVOKED")
	void adminRetryCreatesANewRefundAttemptWithANewKey() throws Exception {
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("pending", "re_1")));
		issueTicket();
		requestRefund().andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from payment.refunds where stripe_refund_id = 're_1'")).isEqualTo(1));
		webhook("evt_failed", "refund.failed", failedRefund("expired_or_canceled_card")).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(statusOf(bookingId)).isEqualTo("REFUND_FAILED"));
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(refund("succeeded", "re_2")));
		events.clear();

		mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/refund").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(TestTokens.ADMIN_ID).roles("ADMIN").bearer()))
				.andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(statusOf(bookingId)).isEqualTo("REFUNDED"));
		// Khớp theo stripe_refund_id (định danh của từng lần thử): created_at và id (Uuid7 theo clock cố định của
		// test) trùng giờ nên không dùng để sắp được.
		assertThat(jdbc.queryForList("select id from payment.refunds", UUID.class)).hasSize(2);
		assertThat(jdbc.queryForObject("select status from payment.refunds where stripe_refund_id = 're_1'",
				String.class)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select status from payment.refunds where stripe_refund_id = 're_2'",
				String.class)).isEqualTo("SUCCEEDED");
		var requests = stripe.findAll(postRequestedFor(urlEqualTo("/v1/refunds")));
		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).getHeader("Idempotency-Key")).isNotEqualTo(requests.get(1).getHeader("Idempotency-Key"));
		assertThat(requests.get(1).getBodyAsString()).contains("amount=950000"); // đúng số tiền đã chốt lần đầu
		assertThat(jdbc.queryForList("select status from ticket.tickets where booking_id = ?", String.class, bookingId))
				.containsOnly("REVOKED");
		// Hoàn thủ công là thao tác quản trị: có đúng một dòng audit với người thao tác (AC-10 của Sprint 5).
		assertThat(jdbc.queryForList(
				"select actor_id from shared.audit_log where action = 'REFUND_RETRIED' and target_id = ?", UUID.class,
				bookingId.toString())).containsExactly(TestTokens.ADMIN_ID);
	}

	@Test
	@DisplayName("Danh sách REFUND_FAILED cho Admin có email liên hệ, sự kiện, tổng tiền, trạng thái; chỉ ADMIN gọi được")
	void adminListsRefundFailedBookingsAndOnlyAdminMay() throws Exception {
		jdbc.update("update booking.bookings set status = 'REFUND_FAILED', refund_failed_at = ? where id = ?",
				Timestamp.from(NOW), bookingId);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/admin/bookings?status=REFUND_FAILED")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(TestTokens.ADMIN_ID).roles("ADMIN").bearer()))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].bookingId")
						.value(bookingId.toString()))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].contactEmail")
						.value(email))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].eventTitle")
						.value("Sự kiện thử"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].totalMinor")
						.value(1_000_000))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].status")
						.value("REFUND_FAILED"));

		for (String roles : new String[] { "CUSTOMER", "ORGANIZER" }) {
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.get("/api/v1/admin/bookings?status=REFUND_FAILED")
					.header(HttpHeaders.AUTHORIZATION, TestTokens.token().roles(roles).bearer()))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/refund")
					.header(HttpHeaders.AUTHORIZATION, TestTokens.token().roles(roles).bearer()))
					.andExpect(status().isForbidden());
		}
		assertThat(statusOf(bookingId)).isEqualTo("REFUND_FAILED");
	}

	@Test
	@DisplayName("Admin hoàn thủ công đơn không ở REFUND_FAILED → 409; đơn không tồn tại → 404")
	void adminRetryOnlyWorksOnRefundFailedBookings() throws Exception {
		String admin = TestTokens.token().subject(TestTokens.ADMIN_ID).roles("ADMIN").bearer();

		mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/refund").header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/admin/bookings/" + UUID.randomUUID() + "/refund")
				.header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());

		assertThat(statusOf(bookingId)).isEqualTo("CONFIRMED");
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	// ---- helpers ----

	private ResultActions requestRefund() throws Exception {
		return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/refund")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()));
	}

	private RefundRequested refundRequested(String reason, long amount) {
		return new RefundRequested(UUID.randomUUID(), NOW, bookingId, customer, eventId, "Sự kiện thử", email, reason,
				amount, "VND", null);
	}

	private void publishRefundRequested(String reason, long amount) {
		RefundRequested message = refundRequested(reason, amount);
		transaction.executeWithoutResult(status -> publisher.publishEvent(message));
	}

	private static ResponseDefinitionBuilder refund(String status) {
		return refund(status, "re_1");
	}

	private static ResponseDefinitionBuilder refund(String status, String id) {
		return aResponse().withHeader("Content-Type", "application/json").withBody("""
				{"id":"%s","object":"refund","amount":950000,"currency":"vnd","status":"%s",
				 "payment_intent":"%s"}
				""".formatted(id, status, INTENT_ID));
	}

	private static String charge() {
		return """
				{"id":"ch_1","object":"charge","payment_intent":"%s","amount":1000000,"amount_refunded":950000,
				 "currency":"vnd","refunded":false}
				""".formatted(INTENT_ID);
	}

	private static String failedRefund(String reason) {
		return """
				{"id":"re_1","object":"refund","payment_intent":"%s","amount":950000,"currency":"vnd",
				 "status":"failed","failure_reason":"%s"}
				""".formatted(INTENT_ID, reason);
	}

	private ResultActions webhook(String eventId, String type, String object) throws Exception {
		String payload = """
				{"id":"%s","object":"event","api_version":"2025-01-27.acacia","created":%d,"livemode":false,
				 "type":"%s","data":{"object":%s}}
				""".formatted(eventId, NOW.getEpochSecond(), type, object);
		return mockMvc.perform(post(WEBHOOK).header("Stripe-Signature", signature(payload))
				.contentType(MediaType.APPLICATION_JSON).content(payload.getBytes(StandardCharsets.UTF_8)));
	}

	/** Header giống Stripe: {@code t=<giây>,v1=HMAC-SHA256(secret, "<t>.<body>")}. */
	private static String signature(String payload) {
		try {
			long timestamp = NOW.getEpochSecond();
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(StripeStubConfiguration.WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"));
			byte[] bytes = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
			return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(bytes);
		}
		catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private String statusOf(UUID booking) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, booking);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private UUID event() {
		UUID id = UUID.randomUUID();
		Instant startsAt = NOW.plus(Duration.ofDays(30));
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', 6,
				    500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(3 * 3600)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		return id;
	}

	/** Booking {@code CONFIRMED} một ghế 1.000.000đ của {@code customer}, ghế đã SOLD. */
	private UUID confirmedBooking() {
		UUID seat = UUID.randomUUID();
		UUID tier = UUID.nameUUIDFromBytes("VIP".getBytes(StandardCharsets.UTF_8));
		jdbc.update("""
				insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
				    seat_number, price_minor)
				values (?, ?, ?, 'VIP', 'A', 'A', 1, 1000000)
				""", eventId, seat, tier);
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, 'CONFIRMED', 1000000, ?, ?)
				""", id, customer, eventId, Timestamp.from(NOW), email);
		jdbc.update("""
				insert into booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
				values (?, ?, ?, ?, 1000000)
				""", id, eventId, seat, tier);
		jdbc.update("update booking.seat_inventory set status = 'SOLD', booking_id = ? where event_id = ? and seat_id = ?",
				id, eventId, seat);
		return id;
	}

	private void issueTicket() {
		UUID seat = jdbc.queryForObject("select seat_id from booking.booking_items where booking_id = ?", UUID.class,
				bookingId);
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, 'VALID', 'token', 'k1', 'Sự kiện thử', ?, 'Sài Gòn Arena', 'A', 'A', 1, 'VIP', ?)
				""", UUID.randomUUID(), bookingId, eventId, seat, customer, Timestamp.from(NOW.plus(Duration.ofDays(30))),
				Timestamp.from(NOW));
	}

	/** Payment đã thu tiền (SUCCEEDED) của booking; {@code fee} là phí nền tảng đã chốt (BR-06). */
	private UUID succeededPayment(UUID booking, long amount, long fee) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, 'VND', 'SUCCEEDED', ?, ?)
				""", id, booking, INTENT_ID, amount, fee, Timestamp.from(NOW), Timestamp.from(NOW));
		return id;
	}

}
