package com.ndz.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.github.tomakehurst.wiremock.client.WireMock;
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
import com.ndz.StripeStubConfiguration;
import com.ndz.TestTokens;

/**
 * Stripe Connect onboarding (plan Sprint 6 việc 1; AC-1, AC-2; BR-05, BR-13; overview.md §7.6): link onboarding tạo
 * Express account một lần; webhook Connect chỉ nhận chữ ký bằng secret riêng, khử trùng theo event id;
 * {@code charges_enabled} đổi thì Catalog cập nhật {@code payout_ready}, mất thì tạm dừng sự kiện đang bán; đủ 5
 * {@code state}. Stripe giả lập bằng WireMock.
 */
@IntegrationTest
class StripeConnectApiTests {

	private static final String ACCOUNT = "acct_connect_test";
	private static final String WEBHOOK = "/api/v1/webhooks/stripe-connect";
	private static final String TITLE = "Connect thử";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	WireMockServer stripe;

	@BeforeEach
	void prepare() {
		cleanUp();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/accounts"))
				.willReturn(json("{\"id\":\"" + ACCOUNT + "\",\"object\":\"account\"}")));
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/account_links")).willReturn(json(
				"{\"object\":\"account_link\",\"url\":\"https://connect.stripe.com/setup/e/x\",\"created\":1,\"expires_at\":1893456000}")));
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/accounts/" + ACCOUNT + "/login_links"))
				.willReturn(json("{\"object\":\"login_link\",\"url\":\"https://connect.stripe.com/express/x\",\"created\":1}")));
		stripe.stubFor(WireMock.get(urlPathEqualTo("/v1/balance")).willReturn(json(
				"{\"object\":\"balance\",\"available\":[{\"amount\":1500000,\"currency\":\"vnd\"}],\"pending\":[],\"livemode\":false}")));
	}

	@AfterEach
	void cleanUp() {
		stripe.resetAll();
		jdbc.update("delete from payment.connected_accounts");
		jdbc.update("delete from payment.stripe_events where stripe_event_id like 'evt_connect_%'");
		jdbc.update("delete from catalog.processed_events");
		jdbc.update("delete from account.user_profiles where email = 'admin@connect.test'");
		jdbc.update("delete from notification.notifications where type = 'STRIPE_RESTRICTED'");
		jdbc.update("delete from catalog.events where title = ?", TITLE);
		jdbc.update("update catalog.organizers set status = 'APPROVED', payout_ready = true where id = ?",
				TestTokens.ORGANIZER_ID);
	}

	@Test
	@DisplayName("Onboarding: tạo Express account một lần, trả link; organizer chưa duyệt → 409 ORGANIZER_NOT_APPROVED")
	void onboardingCreatesTheAccountOnce() throws Exception {
		call(post("/api/v1/organizer/stripe/onboarding-link")).andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/e/x"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty());
		call(post("/api/v1/organizer/stripe/onboarding-link")).andExpect(status().isOk());

		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/accounts")));
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value("incomplete"));
		call(post("/api/v1/organizer/stripe/login-link")).andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://connect.stripe.com/express/x"));

		jdbc.update("update catalog.organizers set status = 'PENDING' where id = ?", TestTokens.ORGANIZER_ID);
		call(post("/api/v1/organizer/stripe/onboarding-link")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORGANIZER_NOT_APPROVED"));
	}

	@Test
	@DisplayName("Chưa kết nối: state none, login-link → 409 STRIPE_NOT_CONNECTED")
	void notConnected() throws Exception {
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value("none"))
				.andExpect(jsonPath("$.balanceMinor").doesNotExist());
		call(post("/api/v1/organizer/stripe/login-link")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("STRIPE_NOT_CONNECTED"));
	}

	@Test
	@DisplayName("AC-2: webhook Connect ký bằng secret của endpoint platform, sai chữ ký, lệch giờ > 5 phút → 400; event trùng không xử lý lại")
	void connectWebhookSignatureAndDeduplication() throws Exception {
		connect();
		String payload = accountUpdated("evt_connect_1", true, true, true, "[]", null);
		long now = Instant.now().getEpochSecond();

		send(payload, header(payload, now, StripeStubConfiguration.WEBHOOK_SECRET)).andExpect(status().isBadRequest());
		send(payload, header(payload, now - 301, StripeStubConfiguration.CONNECT_WEBHOOK_SECRET))
				.andExpect(status().isBadRequest());
		send(payload, "t=" + now + ",v1=deadbeef").andExpect(status().isBadRequest());
		send(payload, header(payload, now, StripeStubConfiguration.CONNECT_WEBHOOK_SECRET)).andExpect(status().isOk());
		send(payload, header(payload, now, StripeStubConfiguration.CONNECT_WEBHOOK_SECRET)).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select count(*) from payment.stripe_events where stripe_event_id = 'evt_connect_1'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-1, AC-7: charges_enabled false → true thì payout_ready = true; true → false thì sự kiện đang bán tự tạm dừng; bật lại không tự mở bán")
	void chargesEnabledDrivesPayoutReadyAndSuspension() throws Exception {
		jdbc.update("update catalog.organizers set payout_ready = false where id = ?", TestTokens.ORGANIZER_ID);
		connect();
		UUID onSale = onSaleEvent();

		signed(accountUpdated("evt_connect_on", true, true, true, "[]", null)).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(15)).until(this::payoutReady);
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value("active"))
				.andExpect(jsonPath("$.balanceMinor").value(1_500_000));

		signed(accountUpdated("evt_connect_off", false, false, true, "[]", "rejected.fraud")).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(15)).until(() -> !payoutReady()
				&& "SUSPENDED".equals(jdbc.queryForObject("select status from catalog.events where id = ?",
						String.class, onSale)));
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value("restricted"));

		signed(accountUpdated("evt_connect_back", true, true, true, "[]", null)).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(15)).until(this::payoutReady);
		assertThat(jdbc.queryForObject("select status from catalog.events where id = ?", String.class, onSale))
				.isEqualTo("SUSPENDED");
	}

	@Test
	@DisplayName("S6-6: tài khoản chuyển sang restricted thì Admin nhận thông báo một lần; event restricted tiếp theo không báo lại")
	void restrictedNotifiesAdminOnce() throws Exception {
		jdbc.update("delete from notification.notifications where type = 'STRIPE_RESTRICTED'");
		jdbc.update("""
				insert into account.user_profiles (user_id, email, full_name, username, roles, enabled, created_at,
				                                   updated_at)
				values (?, 'admin@connect.test', 'Admin', 'admin-connect', array['CUSTOMER', 'ADMIN'], true, now(), now())
				""", UUID.randomUUID());
		connect();
		signed(accountUpdated("evt_connect_r0", true, true, true, "[]", null)).andExpect(status().isOk());

		signed(accountUpdated("evt_connect_r1", false, false, true, "[]", "rejected.fraud")).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(15)).until(() -> restrictedNotifications() > 0);
		int first = restrictedNotifications();

		signed(accountUpdated("evt_connect_r2", false, false, true, "[]", "rejected.other")).andExpect(status().isOk());
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value("restricted"));
		assertThat(restrictedNotifications()).isEqualTo(first);
	}

	@Test
	@DisplayName("Payload account.updated mẫu cho ra đủ trạng thái incomplete / pending / restricted / active")
	void accountStates() throws Exception {
		connect();
		expectState("evt_connect_s1", false, false, "[]", null, "incomplete");
		expectState("evt_connect_s2", false, true, "[\"external_account\"]", "requirements.past_due", "incomplete");
		expectState("evt_connect_s3", false, true, "[]", "requirements.pending_verification", "pending");
		expectState("evt_connect_s4", false, true, "[]", "rejected.other", "restricted");
		expectState("evt_connect_s5", true, true, "[]", null, "active");
	}

	// ---- helpers ----

	private void expectState(String eventId, boolean charges, boolean details, String due, String disabled,
			String state) throws Exception {
		signed(accountUpdated(eventId, charges, charges, details, due, disabled)).andExpect(status().isOk());
		call(get("/api/v1/organizer/stripe/account")).andExpect(jsonPath("$.state").value(state));
	}

	private void connect() throws Exception {
		call(post("/api/v1/organizer/stripe/onboarding-link")).andExpect(status().isOk());
	}

	private int restrictedNotifications() {
		return jdbc.queryForObject("select count(*) from notification.notifications where type = 'STRIPE_RESTRICTED'",
				Integer.class);
	}

	private boolean payoutReady() {
		return Boolean.TRUE.equals(jdbc.queryForObject("select payout_ready from catalog.organizers where id = ?",
				Boolean.class, TestTokens.ORGANIZER_ID));
	}

	private UUID onSaleEvent() {
		UUID id = UUID.randomUUID();
		Instant startsAt = Instant.now().plusSeconds(30 * 86_400);
		jdbc.update("""
				insert into catalog.events (id, organizer_id, venue_id, seat_map_id, title, starts_at, ends_at,
				    sale_starts_at, status, platform_fee_bps, inventory_ready)
				values (?, ?, '00000000-0000-7000-8000-0000000b0001', '00000000-0000-7000-8000-0000000c0001', ?, ?, ?,
				    now() - interval '1 day', 'ON_SALE', 500, true)
				""", id, TestTokens.ORGANIZER_ID, TITLE, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(7200)));
		return id;
	}

	private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer()));
	}

	private ResultActions signed(String payload) throws Exception {
		return send(payload, header(payload, Instant.now().getEpochSecond(), StripeStubConfiguration.CONNECT_WEBHOOK_SECRET));
	}

	private ResultActions send(String payload, String signature) throws Exception {
		return mockMvc.perform(post(WEBHOOK).header("Stripe-Signature", signature)
				.contentType(MediaType.APPLICATION_JSON).content(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private static String accountUpdated(String eventId, boolean charges, boolean payouts, boolean details,
			String currentlyDue, String disabledReason) {
		return """
				{"id":"%s","object":"event","api_version":"2025-06-30.basil","created":%d,"type":"account.updated",
				 "account":"%s","livemode":false,"pending_webhooks":1,"request":{"id":null},
				 "data":{"object":{"id":"%s","object":"account","type":"express","charges_enabled":%s,
				   "payouts_enabled":%s,"details_submitted":%s,
				   "requirements":{"currently_due":%s,"disabled_reason":%s}}}}
				""".formatted(eventId, Instant.now().getEpochSecond(), ACCOUNT, ACCOUNT, charges, payouts, details,
				currentlyDue, disabledReason == null ? "null" : "\"" + disabledReason + "\"");
	}

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

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
		return aResponse().withHeader("Content-Type", "application/json").withBody(body);
	}

}
