package com.ndz.ticket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.event.TicketIssuanceFailed;
import com.ndz.ticket.event.TicketIssued;

/**
 * Nhánh bù trừ khi phát vé (plan Sprint 4 việc 4; AC-5; overview.md §7.3 dòng "Phát hành vé vi phạm UNIQUE"; ADR 0019):
 * lỗi nghiệp vụ (ghế đã có vé) → {@code TicketIssuanceFailed} → hoàn 100% tự động; lỗi hạ tầng → thử lại, không hoàn.
 */
@IntegrationTest
class TicketIssuanceCompensationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final String INTENT_ID = "pi_issuance_1";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	WireMockServer stripe;

	@Autowired
	EventRecorder events;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	IncompleteEventPublications incomplete;

	private final UUID customer = UUID.randomUUID();
	private UUID eventId;

	@BeforeEach
	void prepare() {
		cleanUp();
		events.clear();
		stripe.stubFor(WireMock.post(urlEqualTo("/v1/refunds")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json").withBody("""
						{"id":"re_issuance_1","object":"refund","amount":1000000,"currency":"vnd","status":"succeeded",
						 "payment_intent":"%s"}
						""".formatted(INTENT_ID))));
		eventId = event();
	}

	@AfterEach
	void cleanUp() {
		stripe.resetAll();
		jdbc.execute("alter table ticket.tickets drop constraint if exists ck_test_database_down");
		jdbc.update("delete from shared.event_publication where completion_date is null");
		jdbc.update("delete from payment.processed_events");
		jdbc.update("delete from payment.refunds");
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
		jdbc.update("delete from notification.processed_events");
	}

	@Test
	@DisplayName("AC-5: phát vé vi phạm UNIQUE → TicketIssuanceFailed → booking REFUND_REQUESTED → hoàn 100% → REFUNDED")
	void aSeatThatAlreadyHasALiveTicketIsRefundedInFull() {
		UUID seat = UUID.randomUUID();
		insertSeat(seat);
		UUID otherBooking = insertBooking(UUID.randomUUID(), "CONFIRMED", seat);
		insertTicket(otherBooking, seat, "VALID"); // vé hợp lệ của người khác cho đúng ghế này
		UUID booking = insertBooking(customer, "CONFIRMED", seat);
		insertPayment(booking);

		publish(confirmed(booking, seat));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(statusOf(booking)).isEqualTo("REFUNDED"));
		List<TicketIssuanceFailed> failed = events.of(TicketIssuanceFailed.class);
		assertThat(failed).hasSize(1);
		assertThat(failed.get(0).conflictingSeatIds()).containsExactly(seat);
		assertThat(events.of(RefundRequested.class).get(0).reason()).isEqualTo(RefundRequested.TICKET_ISSUANCE_FAILED);
		stripe.verify(1, postRequestedFor(urlEqualTo("/v1/refunds")).withRequestBody(containing("amount=1000000")));
		assertThat(jdbc.queryForObject("select reason from payment.refunds", String.class))
				.isEqualTo(RefundRequested.TICKET_ISSUANCE_FAILED);
		assertThat(count("select count(*) from ticket.tickets where booking_id = ?", booking)).isZero();
		assertThat(events.of(TicketIssued.class)).isEmpty();
		// Ghế đang thuộc vé của người khác nên không được nhả cho người thứ ba.
		assertThat(count("select count(*) from booking.seat_inventory where seat_id = ? and status = 'SOLD'", seat))
				.isEqualTo(1);
		assertThat(count("select count(*) from ticket.tickets where seat_id = ? and status = 'VALID'", seat))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("AC-5: DB lỗi khi phát vé → thử lại, phát vé thành công khi DB về, không hoàn")
	void aDatabaseFailureIsRetriedAndNeverRefunded() {
		UUID seat = UUID.randomUUID();
		insertSeat(seat);
		UUID booking = insertBooking(customer, "CONFIRMED", seat);
		insertPayment(booking);
		// Mô phỏng DB từ chối ghi vé: mọi INSERT mới vào ticket.tickets lỗi cho tới khi gỡ ràng buộc.
		jdbc.execute("alter table ticket.tickets add constraint ck_test_database_down check (false) not valid");

		publish(confirmed(booking, seat));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(
				"select count(*) from shared.event_publication where completion_date is null "
						+ "and serialized_event like '%\"bookingId\":\"" + booking + "\"%'")).isPositive());
		assertThat(count("select count(*) from ticket.tickets where booking_id = ?", booking)).isZero();
		assertThat(events.of(TicketIssuanceFailed.class)).isEmpty();
		assertThat(events.of(RefundRequested.class)).isEmpty();
		assertThat(statusOf(booking)).isEqualTo("CONFIRMED");

		jdbc.execute("alter table ticket.tickets drop constraint ck_test_database_down"); // DB trở lại
		incomplete.resubmitIncompletePublications(publication -> true);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(
				() -> assertThat(count("select count(*) from ticket.tickets where booking_id = ? and status = 'VALID'",
						booking)).isEqualTo(1));
		assertThat(events.of(TicketIssued.class)).hasSize(1);
		assertThat(events.of(RefundRequested.class)).isEmpty();
		assertThat(statusOf(booking)).isEqualTo("CONFIRMED");
		stripe.verify(0, postRequestedFor(urlEqualTo("/v1/refunds")));
	}

	// ---- helpers ----

	private void publish(BookingConfirmed message) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(message));
	}

	private BookingConfirmed confirmed(UUID booking, UUID seat) {
		return new BookingConfirmed(UUID.randomUUID(), NOW, booking, customer, eventId, "Sự kiện thử",
				NOW.plus(Duration.ofDays(30)), "Sài Gòn Arena", "customer@tickethub.local", 1_000_000L, "VND",
				List.of(new BookingConfirmed.Seat(seat, "A", "A", 1, UUID.nameUUIDFromBytes("VIP".getBytes(
						StandardCharsets.UTF_8)), "VIP", 1_000_000L)));
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
				""", id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(3 * 3600)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		return id;
	}

	private void insertSeat(UUID seat) {
		jdbc.update("""
				insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
				    seat_number, price_minor)
				values (?, ?, ?, 'VIP', 'A', 'A', 1, 1000000)
				""", eventId, seat, UUID.nameUUIDFromBytes("VIP".getBytes(StandardCharsets.UTF_8)));
	}

	/** Booking của {@code user} cho đúng ghế {@code seat}; ghế (mỗi ghế một chủ) thuộc booking tạo sau cùng. */
	private UUID insertBooking(UUID user, String status, UUID seat) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, 1000000, ?, 'customer@tickethub.local')
				""", id, user, eventId, status, Timestamp.from(NOW));
		UUID tier = UUID.nameUUIDFromBytes("VIP".getBytes(StandardCharsets.UTF_8));
		jdbc.update("""
				insert into booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
				values (?, ?, ?, ?, 1000000)
				""", id, eventId, seat, tier);
		jdbc.update("update booking.seat_inventory set status = 'SOLD', booking_id = ? where event_id = ? and seat_id = ?",
				id, eventId, seat);
		return id;
	}

	private void insertTicket(UUID booking, UUID seat, String status) {
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, 'token', 'k1', 'Sự kiện thử', ?, 'Sài Gòn Arena', 'A', 'A', 1, 'VIP', ?)
				""", UUID.randomUUID(), booking, eventId, seat, UUID.randomUUID(), status,
				Timestamp.from(NOW.plus(Duration.ofDays(30))), Timestamp.from(NOW));
	}

	private void insertPayment(UUID booking) {
		jdbc.update("""
				insert into payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				    application_fee_minor, currency, status, created_at, updated_at)
				values (?, ?, ?, 1000000, 50000, 'VND', 'SUCCEEDED', ?, ?)
				""", UUID.randomUUID(), booking, INTENT_ID, Timestamp.from(NOW), Timestamp.from(NOW));
	}

}
