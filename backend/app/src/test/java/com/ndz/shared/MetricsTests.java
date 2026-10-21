package com.ndz.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.booking.event.TicketIssuanceFailed;
import com.ndz.booking.service.BookingInvariantService;
import com.ndz.ticket.service.TicketInvariantService;

/**
 * Metric nghiệp vụ và chỉ số bất biến (plan Sprint 4 việc 6; overview.md §16.1, §16.3): có trên
 * {@code /actuator/prometheus}, đếm đúng theo event, và truy vấn ghế trùng / đơn REFUND_FAILED phản ánh dữ liệu thật.
 */
@IntegrationTest
class MetricsTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MeterRegistry metrics;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	TicketInvariantService ticketInvariants;

	@Autowired
	BookingInvariantService bookingInvariants;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbc.update("delete from ticket.tickets");
		jdbc.execute("create unique index if not exists uq_ticket_seat_active on ticket.tickets (event_id, seat_id) "
				+ "where status <> 'REVOKED'");
		jdbc.update("delete from booking.revoked_tickets");
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
	}

	@Test
	@DisplayName("/actuator/prometheus không cần token và có đủ metric nghiệp vụ, bất biến và resilience4j")
	void prometheusEndpointExposesBusinessInvariantAndResilienceMetrics() throws Exception {
		String body = mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(body).contains("tickethub_booking_held_total", "tickethub_booking_confirmed_total",
				"tickethub_tickets_sold_total", "tickethub_tickets_issued_total",
				"tickethub_tickets_issuance_failed_total", "tickethub_payment_succeeded_total",
				"tickethub_payment_failed_total", "tickethub_refund_completed_total", "tickethub_refund_failed_total",
				"tickethub_invariant_duplicate_seat_tickets", "tickethub_invariant_refund_failed_bookings",
				"tickethub_payment_succeeded_unsettled", "resilience4j_circuitbreaker_state{",
				"resilience4j_bulkhead_available_concurrent_calls{");
		assertThat(body).contains("name=\"stripe\"");
	}

	@Test
	@DisplayName("Counter tickethub.tickets.issuance_failed tăng khi có TicketIssuanceFailed")
	void issuanceFailedCounterIncrementsWithTheEvent() {
		double before = metrics.get("tickethub.tickets.issuance_failed").counter().count();

		transaction.executeWithoutResult(status -> publisher.publishEvent(new TicketIssuanceFailed(UUID.randomUUID(),
				NOW, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sự kiện thử", "a@b.c",
				List.of(UUID.randomUUID()), 1_000_000L, "VND")));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
				metrics.get("tickethub.tickets.issuance_failed").counter().count()).isEqualTo(before + 1));
	}

	@Test
	@DisplayName("§10: truy vấn ghế trùng = 0 khi sạch; = 1 khi chốt chặn UNIQUE bị mất và có hai vé còn hiệu lực cùng ghế")
	void duplicateSeatQueryReportsTheInvariantViolation() {
		assertThat(ticketInvariants.checkDuplicateSeatTickets()).isZero();
		assertThat(metrics.get("tickethub.invariant.duplicate_seat_tickets").gauge().value()).isZero();

		UUID event = UUID.randomUUID();
		UUID seat = UUID.randomUUID();
		jdbc.execute("drop index ticket.uq_ticket_seat_active"); // mô phỏng chốt chặn cuối bị mất
		insertTicket(event, seat, "VALID");
		insertTicket(event, seat, "USED");
		insertTicket(event, seat, "REVOKED"); // vé đã thu hồi không tính

		assertThat(ticketInvariants.checkDuplicateSeatTickets()).isEqualTo(1);
		assertThat(metrics.get("tickethub.invariant.duplicate_seat_tickets").gauge().value()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Gauge đơn REFUND_FAILED bằng số đơn đang chờ Admin hoàn thủ công")
	void refundFailedGaugeCountsBookingsWaitingForAnAdmin() {
		assertThat(bookingInvariants.checkRefundFailedBookings()).isZero();

		UUID event = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Arena', ?, 'HCM', ?, ?, ?, ?, 'ON_SALE', 6, 500, ?)
				""", event, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(86_400)), Timestamp.from(NOW.plusSeconds(90_000)),
				Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW));
		for (String status : List.of("REFUND_FAILED", "REFUND_FAILED", "REFUNDED", "CONFIRMED")) {
			jdbc.update("""
					insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
					values (?, ?, ?, ?, 1000000, ?, 'a@b.c')
					""", UUID.randomUUID(), UUID.randomUUID(), event, status, Timestamp.from(NOW));
		}

		assertThat(bookingInvariants.checkRefundFailedBookings()).isEqualTo(2);
		assertThat(metrics.get("tickethub.invariant.refund_failed_bookings").gauge().value()).isEqualTo(2.0);
	}

	private void insertTicket(UUID event, UUID seat, String status) {
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, 'token', 'k1', 'Sự kiện thử', ?, 'Arena', 'A', 'A', 1, 'VIP', ?)
				""", UUID.randomUUID(), UUID.randomUUID(), event, seat, UUID.randomUUID(), status,
				Timestamp.from(NOW), Timestamp.from(NOW));
	}

}
