package com.ndz.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * Chi tiết thanh toán của đơn cho Admin (plan Sprint 6 hạng mục 7, màn #29; overview.md §11.2): khoản thu, các lần
 * hoàn và dòng thời gian Stripe sắp theo thời gian; chỉ Admin xem được.
 */
@IntegrationTest
class AdminOrderPaymentApiTests {

	private static final String INTENT = "pi_admin_detail_test";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private UUID bookingId;

	@BeforeEach
	void prepare() {
		cleanUp();
		bookingId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		Instant base = Instant.now().minusSeconds(600);
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor, application_fee_minor,
				    currency, status, created_at, updated_at, destination_account_id)
				values (?, ?, ?, 1000000, 50000, 'VND', 'SUCCEEDED', ?, ?, 'acct_dest')
				""", paymentId, bookingId, INTENT, Timestamp.from(base), Timestamp.from(base));
		jdbc.update("""
				insert into payment.stripe_events (stripe_event_id, type, payload, received_at, processed_at)
				values ('evt_admin_detail_1', 'payment_intent.succeeded', ?::jsonb, ?, ?),
				       ('evt_admin_detail_2', 'charge.refunded', ?::jsonb, ?, null),
				       ('evt_admin_detail_other', 'payment_intent.succeeded', '{"data":{"object":{"id":"pi_other"}}}'::jsonb, ?, ?)
				""", "{\"data\":{\"object\":{\"id\":\"" + INTENT + "\"}}}", Timestamp.from(base.plusSeconds(60)),
				Timestamp.from(base.plusSeconds(61)), "{\"data\":{\"object\":{\"payment_intent\":\"" + INTENT + "\"}}}",
				Timestamp.from(base.plusSeconds(300)), Timestamp.from(base.plusSeconds(70)), Timestamp.from(base.plusSeconds(71)));
		jdbc.update("""
				insert into payment.refunds (id, payment_id, amount_minor, reason, status, created_at, updated_at,
				    failure_reason)
				values (?, ?, 950000, 'CUSTOMER_REQUEST', 'FAILED', ?, ?, 'card_declined')
				""", UUID.randomUUID(), paymentId, Timestamp.from(base.plusSeconds(120)), Timestamp.from(base.plusSeconds(120)));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from payment.refunds where payment_id in (select id from payment.payments where stripe_payment_intent_id = ?)", INTENT);
		jdbc.update("delete from payment.payments where stripe_payment_intent_id = ?", INTENT);
		jdbc.update("delete from payment.stripe_events where stripe_event_id like 'evt_admin_detail_%'");
	}

	@Test
	@DisplayName("Admin xem khoản thu, lần hoàn lỗi và dòng thời gian: chỉ event của đúng PaymentIntent, cũ nhất trước")
	void detailWithTimeline() throws Exception {
		call(bookingId, TestTokens.ADMIN_ID, "ADMIN").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.amountMinor").value(1_000_000))
				.andExpect(jsonPath("$.applicationFeeMinor").value(50_000))
				.andExpect(jsonPath("$.destinationAccountId").value("acct_dest"))
				.andExpect(jsonPath("$.refunds.length()").value(1))
				.andExpect(jsonPath("$.refunds[0].status").value("FAILED"))
				.andExpect(jsonPath("$.refunds[0].failureReason").value("card_declined"))
				.andExpect(jsonPath("$.timeline.length()").value(4))
				.andExpect(jsonPath("$.timeline[0].kind").value("PAYMENT_CREATED"))
				.andExpect(jsonPath("$.timeline[1].label").value("payment_intent.succeeded"))
				.andExpect(jsonPath("$.timeline[2].kind").value("REFUND"))
				.andExpect(jsonPath("$.timeline[3].label").value("charge.refunded"))
				.andExpect(jsonPath("$.timeline[3].unprocessed").value(true));
	}

	@Test
	@DisplayName("Đơn chưa có thanh toán → 404; không phải Admin → 403")
	void notFoundAndForbidden() throws Exception {
		call(UUID.randomUUID(), TestTokens.ADMIN_ID, "ADMIN").andExpect(status().isNotFound());
		call(bookingId, TestTokens.ORGANIZER_ID, "ORGANIZER").andExpect(status().isForbidden());
	}

	private ResultActions call(UUID booking, UUID subject, String role) throws Exception {
		return mockMvc.perform(get("/api/v1/admin/bookings/" + booking + "/payment").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(subject).roles(role).bearer()));
	}

}
