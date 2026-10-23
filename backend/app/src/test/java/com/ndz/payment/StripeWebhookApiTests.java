package com.ndz.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.StripeStubConfiguration;
import com.ndz.payment.event.PaymentFailed;
import com.ndz.booking.event.PaymentSucceeded;

/**
 * Webhook Stripe (plan Sprint 3 việc 3; overview.md §7.3, §7.7, §13.4; ADR 0019): chữ ký trên raw body, dung sai giờ
 * 5 phút, khử trùng theo {@code stripe_event_id}, máy trạng thái payment và event phát ra. Webhook không có token.
 */
@IntegrationTest
@RecordApplicationEvents
class StripeWebhookApiTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String WEBHOOK = "/api/v1/webhooks/stripe";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	ApplicationEvents applicationEvents;

	private UUID bookingId;
	private UUID paymentId;
	private String intentId;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
		bookingId = UUID.randomUUID();
		paymentId = UUID.randomUUID();
		intentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
		insertPayment("REQUIRES_PAYMENT");
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from payment.stripe_events");
		jdbc.update("delete from payment.payments");
	}

	// ---- Chữ ký (AC-4) ----

	@Test
	@DisplayName("AC-4: sai chữ ký thì 400, không ghi gì, không đổi trạng thái")
	void wrongSignatureIsRejected() throws Exception {
		String payload = event("evt_1", "payment_intent.succeeded", "succeeded");

		send(payload, "t=" + NOW.getEpochSecond() + ",v1=" + "0".repeat(64)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");
		assertThat(count("select count(*) from payment.stripe_events")).isZero();
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isZero();
	}

	@Test
	@DisplayName("AC-4: ký bằng secret khác thì 400")
	void signatureWithAnotherSecretIsRejected() throws Exception {
		String payload = event("evt_1", "payment_intent.succeeded", "succeeded");

		send(payload, header(payload, NOW.getEpochSecond(), "whsec_someone_else")).andExpect(status().isBadRequest());

		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");
	}

	@Test
	@DisplayName("AC-4: thiếu header Stripe-Signature thì 400")
	void missingSignatureHeaderIsRejected() throws Exception {
		mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
				.content(event("evt_1", "payment_intent.succeeded", "succeeded"))).andExpect(status().isBadRequest());

		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");
	}

	@Test
	@DisplayName("AC-4: lệch giờ ký quá 5 phút thì 400; đúng 5 phút vẫn nhận")
	void timestampSkewOverFiveMinutesIsRejected() throws Exception {
		String payload = event("evt_old", "payment_intent.succeeded", "succeeded");

		send(payload, header(payload, NOW.minusSeconds(301).getEpochSecond())).andExpect(status().isBadRequest());
		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");

		send(payload, header(payload, NOW.minusSeconds(300).getEpochSecond())).andExpect(status().isOk());
		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
	}

	@Test
	@DisplayName("Chữ ký tính trên raw body: sửa một byte thì bị từ chối, giữ nguyên khoảng trắng và Unicode thì nhận")
	void signatureCoversTheRawBodyByteForByte() throws Exception {
		String payload = event("evt_raw", "payment_intent.succeeded", "succeeded").replace("{", "{  \n").replace("}", "\t}");
		String signature = header(payload, NOW.getEpochSecond());

		send(payload.replace("evt_raw", "evt_rax"), signature).andExpect(status().isBadRequest());
		send(payload, signature).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Payload không phải JSON: 400")
	void garbagePayloadIsRejected() throws Exception {
		String payload = "đây không phải JSON";

		send(payload, header(payload, NOW.getEpochSecond())).andExpect(status().isBadRequest());
	}

	// ---- Máy trạng thái (§7.7) ----

	@Test
	@DisplayName("payment_intent.succeeded: REQUIRES_PAYMENT → SUCCEEDED, phát PaymentSucceeded một lần, ghi stripe_events")
	void succeededMovesThePaymentAndPublishesTheEvent() throws Exception {
		signedSend("evt_ok", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		var published = applicationEvents.stream(PaymentSucceeded.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.get(0).paymentId()).isEqualTo(paymentId);
		assertThat(published.get(0).bookingId()).isEqualTo(bookingId);
		assertThat(published.get(0).amountMinor()).isEqualTo(1_000_000);
		assertThat(published.get(0).currency()).isEqualTo("VND");
		assertThat(jdbc.queryForMap("select type, processed_at from payment.stripe_events "
				+ "where stripe_event_id = 'evt_ok'")).containsEntry("type", "payment_intent.succeeded")
				.doesNotContainEntry("processed_at", null);
	}

	@Test
	@DisplayName("AC-4: cùng stripe_event_id gửi hai lần thì chỉ xử lý một lần")
	void sameEventTwiceIsProcessedOnce() throws Exception {
		signedSend("evt_dup", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());
		signedSend("evt_dup", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isEqualTo(1);
		assertThat(count("select count(*) from payment.stripe_events where stripe_event_id = 'evt_dup'")).isEqualTo(1);
	}

	@Test
	@DisplayName("Hai event khác nhau cho cùng PaymentIntent: chỉ lần đầu phát PaymentSucceeded")
	void aSecondSucceededEventWithAnotherIdIsIgnored() throws Exception {
		signedSend("evt_a", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());
		signedSend("evt_b", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isEqualTo(1);
		assertThat(count("select count(*) from payment.stripe_events")).isEqualTo(2);
	}

	@Test
	@DisplayName("processing rồi succeeded: REQUIRES_PAYMENT → PROCESSING → SUCCEEDED")
	void processingThenSucceeded() throws Exception {
		signedSend("evt_p", "payment_intent.processing", "processing").andExpect(status().isOk());
		assertThat(statusOfPayment()).isEqualTo("PROCESSING");

		signedSend("evt_s", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());
		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Sai thứ tự: processing đến sau succeeded bị bỏ qua, trạng thái giữ SUCCEEDED")
	void processingAfterSucceededIsIgnored() throws Exception {
		signedSend("evt_s", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		signedSend("evt_late", "payment_intent.processing", "processing").andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("payment_failed khi REQUIRES_PAYMENT: giữ nguyên trạng thái, phát PaymentFailed kèm mã lỗi (AC-3)")
	void paymentFailedKeepsTheStateAndPublishesAFailure() throws Exception {
		signedSend("evt_f", "payment_intent.payment_failed", "requires_payment_method", "card_declined")
				.andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");
		var failures = applicationEvents.stream(PaymentFailed.class).toList();
		assertThat(failures).hasSize(1);
		assertThat(failures.get(0).failureCode()).isEqualTo("card_declined");
		assertThat(failures.get(0).bookingId()).isEqualTo(bookingId);
	}

	@Test
	@DisplayName("payment_failed khi PROCESSING: quay về REQUIRES_PAYMENT để thử thẻ khác, rồi succeeded vẫn được (AC-3)")
	void failedWhileProcessingReturnsToRequiresPaymentAndAnotherCardCanSucceed() throws Exception {
		signedSend("evt_p", "payment_intent.processing", "processing").andExpect(status().isOk());
		signedSend("evt_f", "payment_intent.payment_failed", "requires_payment_method", "card_declined")
				.andExpect(status().isOk());
		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");

		signedSend("evt_s", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		assertThat(count("select count(*) from payment.payments")).isEqualTo(1);
	}

	@Test
	@DisplayName("payment_failed đến sau succeeded: bỏ qua, không phát PaymentFailed")
	void failedAfterSucceededIsIgnored() throws Exception {
		signedSend("evt_s", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		signedSend("evt_f", "payment_intent.payment_failed", "requires_payment_method", "card_declined")
				.andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("SUCCEEDED");
		assertThat(applicationEvents.stream(PaymentFailed.class).count()).isZero();
	}

	@Test
	@DisplayName("succeeded cho payment đã CANCELED: bỏ qua, không phát PaymentSucceeded")
	void succeededForACanceledPaymentIsIgnored() throws Exception {
		jdbc.update("update payment.payments set status = 'CANCELED' where id = ?", paymentId);

		signedSend("evt_s", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("CANCELED");
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isZero();
	}

	@Test
	@DisplayName("PaymentIntent không thuộc payment nào, hoặc loại event không xử lý: 200 và không đổi gì")
	void unknownIntentAndUnhandledTypesAreAcknowledgedAndIgnored() throws Exception {
		String foreign = event("evt_x", "payment_intent.succeeded", "succeeded").replace(intentId, "pi_not_ours");
		send(foreign, header(foreign, NOW.getEpochSecond())).andExpect(status().isOk());

		String other = """
				{"id":"evt_charge","object":"event","type":"charge.succeeded","created":1,
				 "data":{"object":{"id":"ch_1","object":"charge","amount":1000000,"currency":"vnd"}}}
				""";
		send(other, header(other, NOW.getEpochSecond())).andExpect(status().isOk());

		assertThat(statusOfPayment()).isEqualTo("REQUIRES_PAYMENT");
		assertThat(applicationEvents.stream(PaymentSucceeded.class).count()).isZero();
	}

	@Test
	@DisplayName("Webhook không cần token (§13.1): gọi không có Authorization vẫn được xử lý")
	void webhookNeedsNoBearerToken() throws Exception {
		signedSend("evt_open", "payment_intent.succeeded", "succeeded").andExpect(status().isOk());
	}

	// ---- helpers ----

	private ResultActions signedSend(String eventId, String type, String intentStatus) throws Exception {
		return signedSend(eventId, type, intentStatus, null);
	}

	private ResultActions signedSend(String eventId, String type, String intentStatus, String failureCode)
			throws Exception {
		String payload = event(eventId, type, intentStatus, failureCode);
		return send(payload, header(payload, NOW.getEpochSecond()));
	}

	private ResultActions send(String payload, String signature) throws Exception {
		return mockMvc.perform(post(WEBHOOK).header("Stripe-Signature", signature)
				.contentType(MediaType.APPLICATION_JSON).content(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private String event(String eventId, String type, String intentStatus) {
		return event(eventId, type, intentStatus, null);
	}

	private String event(String eventId, String type, String intentStatus, String failureCode) {
		String error = failureCode == null ? "" : ",\"last_payment_error\":{\"code\":\"%s\",\"type\":\"card_error\"}"
				.formatted(failureCode);
		return """
				{"id":"%s","object":"event","api_version":"2025-01-27.acacia","created":%d,"livemode":false,
				 "type":"%s","data":{"object":{"id":"%s","object":"payment_intent","amount":1000000,
				 "currency":"vnd","status":"%s","note":"Vé đã bán"%s}}}
				""".formatted(eventId, NOW.getEpochSecond(), type, intentId, intentStatus, error);
	}

	private static String header(String payload, long timestamp) {
		return header(payload, timestamp, StripeStubConfiguration.WEBHOOK_SECRET);
	}

	/** Header giống Stripe: {@code t=<giây>,v1=HMAC-SHA256(secret, "<t>.<body>")}. */
	private static String header(String payload, long timestamp, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signature = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
			return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
		}
		catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private String statusOfPayment() {
		return jdbc.queryForObject("select status from payment.payments where id = ?", String.class, paymentId);
	}

	private int count(String sql) {
		Integer value = jdbc.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

	private void insertPayment(String status) {
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, 1000000, 50000, 'VND', ?, ?, ?)
				""", paymentId, bookingId, intentId, status, Timestamp.from(NOW), Timestamp.from(NOW));
	}

}
