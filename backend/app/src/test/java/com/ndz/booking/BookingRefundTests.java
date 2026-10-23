package com.ndz.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.event.TicketRevoked;
import com.ndz.booking.service.BookingRefundService;

/**
 * Yêu cầu hoàn vé (plan Sprint 4 việc 1; AC-1 phần Booking, AC-2, AC-3; BR-07, BR-09; overview.md §7.4, §7.7; ADR 0026):
 * mốc 48 giờ, thu hồi vé ngay, ghế chỉ trống lại sau khi mọi vé đã thu hồi.
 */
@IntegrationTest
class BookingRefundTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final Duration CUTOFF = Duration.ofHours(48);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	EventRecorder events;

	@Autowired
	BookingRefundService refunds;

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
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
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
	}

	// ---- BR-07: mốc 48 giờ ----

	@Test
	@DisplayName("BR-07: đúng 48 giờ trước giờ diễn vẫn được hoàn; vé 1.000.000đ phí 5% thì nhận lại 950.000đ")
	void exactlyFortyEightHoursBeforeTheEventIsStillRefundable() throws Exception {
		UUID event = event(NOW.plus(CUTOFF));
		UUID booking = confirmedBooking(event, 1);

		refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.bookingId").value(booking.toString()))
				.andExpect(jsonPath("$.status").value("REFUND_REQUESTED"))
				.andExpect(jsonPath("$.refundAmountMinor").value(950_000));

		assertThat(statusOf(booking)).isEqualTo("REFUND_REQUESTED");
		assertThat(jdbc.queryForObject("select refund_amount_minor from booking.bookings where id = ?", Long.class,
				booking)).isEqualTo(950_000L);
		List<RefundRequested> requested = events.of(RefundRequested.class);
		assertThat(requested).hasSize(1);
		assertThat(requested.get(0).reason()).isEqualTo(RefundRequested.CUSTOMER_REQUEST);
		assertThat(requested.get(0).refundAmountMinor()).isEqualTo(950_000L);
	}

	@Test
	@DisplayName("BR-07: thiếu 1 giây so với 48 giờ thì 422 REFUND_WINDOW_CLOSED và booking không đổi")
	void oneSecondShortOfFortyEightHoursIsRefused() throws Exception {
		UUID event = event(NOW.plus(CUTOFF).minusSeconds(1));
		UUID booking = confirmedBooking(event, 1);

		refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("REFUND_WINDOW_CLOSED"));

		assertThat(statusOf(booking)).isEqualTo("CONFIRMED");
		assertThat(events.of(RefundRequested.class)).isEmpty();
	}

	@Test
	@DisplayName("GET /me/bookings báo sẵn số vé, phí không hoàn, số tiền nhận lại và hạn hoàn của đơn CONFIRMED")
	void myBookingsExposeTheRefundTermsOfAConfirmedBooking() throws Exception {
		Instant startsAt = NOW.plus(Duration.ofDays(30));
		UUID event = event(startsAt);
		UUID booking = confirmedBooking(event, 2);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(customer).bearer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].bookingId").value(booking.toString()))
				.andExpect(jsonPath("$[0].ticketCount").value(2)).andExpect(jsonPath("$[0].totalMinor").value(2_000_000))
				.andExpect(jsonPath("$[0].platformFeeMinor").value(100_000))
				.andExpect(jsonPath("$[0].refundableAmountMinor").value(1_900_000))
				.andExpect(jsonPath("$[0].refundDeadline").value(startsAt.minus(CUTOFF).toString()));
	}

	// ---- AC-2: các lỗi còn lại ----

	@Test
	@DisplayName("AC-2: booking chưa CONFIRMED hoặc đã yêu cầu hoàn thì 409 BOOKING_NOT_REFUNDABLE")
	void onlyAConfirmedBookingCanBeRefunded() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		for (String state : List.of("PENDING_PAYMENT", "EXPIRED", "CANCELLED", "REFUND_REQUESTED", "REFUNDED",
				"REFUND_FAILED")) {
			UUID booking = insertBooking(event, customer, state, seats(event, 1).get(0));

			refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BOOKING_NOT_REFUNDABLE"));

			assertThat(statusOf(booking)).isEqualTo(state);
		}
	}

	@Test
	@DisplayName("AC-2: booking của người khác hoặc không tồn tại thì 404, không lộ là có tồn tại")
	void someoneElsesBookingIsNotFound() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 1);

		refund(UUID.randomUUID(), booking, UUID.randomUUID().toString()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
		refund(customer, UUID.randomUUID(), UUID.randomUUID().toString()).andExpect(status().isNotFound());

		assertThat(statusOf(booking)).isEqualTo("CONFIRMED");
	}

	@Test
	void refundWithoutIdempotencyKeyIs400AndWithoutLoginIs401() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 1);

		mockMvc.perform(post("/api/v1/bookings/" + booking + "/refund").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
		mockMvc.perform(post("/api/v1/bookings/" + booking + "/refund").header("Idempotency-Key", "k-1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("AC-2: gửi lại cùng Idempotency-Key thì nhận lại đúng response và chỉ phát RefundRequested một lần")
	void sameIdempotencyKeyReturnsTheSameResponse() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 1);
		String key = UUID.randomUUID().toString();

		String first = refund(customer, booking, key).andExpect(status().isAccepted()).andReturn().getResponse()
				.getContentAsString();
		refund(customer, booking, key).andExpect(status().isAccepted()).andExpect(content().json(first));

		assertThat(events.of(RefundRequested.class)).hasSize(1);
	}

	@Test
	@DisplayName("Hai yêu cầu hoàn đồng thời với hai key khác nhau: một cái 202, cái kia 409")
	void twoDifferentKeysRaceExactlyOnceWins() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Integer> codes;
		try {
			List<Future<Integer>> futures = List.of(
					pool.submit(() -> refund(customer, booking, "race-a").andReturn().getResponse().getStatus()),
					pool.submit(() -> refund(customer, booking, "race-b").andReturn().getResponse().getStatus()));
			codes = new ArrayList<>();
			for (Future<Integer> future : futures) {
				codes.add(future.get(30, TimeUnit.SECONDS));
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(codes).containsExactlyInAnyOrder(202, 409);
		assertThat(events.of(RefundRequested.class)).hasSize(1);
	}

	// ---- AC-3 / BR-09: thu hồi vé và nhả ghế ----

	@Test
	@DisplayName("AC-3: ngay sau yêu cầu hoàn mọi vé REVOKED; ghế chỉ về AVAILABLE sau khi đủ TicketRevoked")
	void ticketsAreRevokedAtOnceAndSeatsFreedOnlyAfterEveryTicketIsRevoked() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 2);
		List<UUID> tickets = issueTickets(booking, event);

		refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(jdbc.queryForList("select status from ticket.tickets where booking_id = ?", String.class,
					booking)).containsOnly("REVOKED");
			assertThat(events.of(TicketRevoked.class)).hasSize(2);
		});
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(
				"select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE' "
						+ "and booking_id is null", event)).isEqualTo(2));
		assertThat(events.of(TicketRevoked.class).stream().map(TicketRevoked::ticketId))
				.containsExactlyInAnyOrderElementsOf(tickets);
	}

	@Test
	@DisplayName("ADR 0026: mới thu hồi 1/2 vé thì ghế vẫn SOLD; nhận lại cùng TicketRevoked không nhả sớm")
	void seatsStaySoldUntilTheLastTicketIsRevoked() {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = insertBooking(event, customer, "REFUND_REQUESTED", seats(event, 2));
		List<UUID> tickets = issueTickets(booking, event);

		TicketRevoked first = revoked(booking, tickets.get(0));
		refunds.onTicketRevoked(first);
		refunds.onTicketRevoked(first); // gửi lại đúng message
		refunds.onTicketRevoked(revoked(booking, tickets.get(0))); // cùng vé, message mới

		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				booking)).isEqualTo(2);
		assertThat(count("select count(*) from booking.revoked_tickets where booking_id = ?", booking)).isEqualTo(1);

		refunds.onTicketRevoked(revoked(booking, tickets.get(1)));

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE' "
				+ "and booking_id is null", event)).isEqualTo(2);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'SOLD'", event))
				.isZero();
	}

	@Test
	@DisplayName("BR-09: yêu cầu hoàn đến trước khi vé kịp phát thì vé phát sau ra đời đã REVOKED và ghế vẫn được nhả")
	void refundRequestedBeforeTicketsAreIssuedStillRevokesThem() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 2);

		refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(15)).untilAsserted(
				() -> assertThat(count("select count(*) from ticket.revoked_bookings where booking_id = ?", booking))
						.isEqualTo(1));
		assertThat(count("select count(*) from ticket.tickets where booking_id = ?", booking)).isZero();

		transaction.executeWithoutResult(status -> publisher.publishEvent(confirmed(booking, event, 2)));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(jdbc.queryForList("select status from ticket.tickets where booking_id = ?", String.class,
					booking)).hasSize(2).containsOnly("REVOKED");
			assertThat(count("select count(*) from booking.seat_inventory where event_id = ? "
					+ "and status = 'AVAILABLE'", event)).isEqualTo(2);
		});
	}

	@Test
	@DisplayName("Sau khi hoàn, ghế đã nhả được người khác giữ ngay")
	void aRefundedSeatCanBeHeldByAnotherCustomer() throws Exception {
		UUID event = event(NOW.plus(Duration.ofDays(30)));
		UUID booking = confirmedBooking(event, 1);
		issueTickets(booking, event);
		UUID seat = jdbc.queryForObject("select seat_id from booking.booking_items where booking_id = ?", UUID.class,
				booking);

		refund(customer, booking, UUID.randomUUID().toString()).andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(
				"select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE'", event))
						.isEqualTo(1));

		mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(UUID.randomUUID()).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
				.content("{\"eventId\":\"%s\",\"seatIds\":[\"%s\"]}".formatted(event, seat)))
				.andExpect(status().isCreated());
	}

	// ---- helpers ----

	private ResultActions refund(UUID user, UUID bookingId, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/refund")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", key));
	}

	private TicketRevoked revoked(UUID booking, UUID ticket) {
		return new TicketRevoked(UUID.randomUUID(), NOW, ticket, booking, UUID.randomUUID());
	}

	private String statusOf(UUID bookingId) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, bookingId);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private UUID event(Instant startsAt) {
		UUID id = UUID.randomUUID();
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

	private List<UUID> seats(UUID event, int count) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID seat = UUID.randomUUID();
			ids.add(seat);
			jdbc.update("""
					insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
					    seat_number, price_minor)
					values (?, ?, ?, 'VIP', 'A', 'A', ?, 1000000)
					""", event, seat, UUID.nameUUIDFromBytes("VIP".getBytes()), i + 1);
		}
		return ids;
	}

	/** Booking {@code CONFIRMED} của {@code customer}: {@code seatCount} ghế, mỗi ghế 1.000.000đ. */
	private UUID confirmedBooking(UUID event, int seatCount) {
		return insertBooking(event, customer, "CONFIRMED", seats(event, seatCount));
	}

	private UUID insertBooking(UUID event, UUID user, String status, UUID seat) {
		return insertBooking(event, user, status, List.of(seat));
	}

	private UUID insertBooking(UUID event, UUID user, String status, List<UUID> seats) {
		UUID bookingId = UUID.randomUUID();
		long total = 1_000_000L * seats.size();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, ?, ?, 'customer@tickethub.local')
				""", bookingId, user, event, status, total, Timestamp.from(NOW));
		for (UUID seat : seats) {
			jdbc.update("""
					insert into booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
					values (?, ?, ?, ?, 1000000)
					""", bookingId, event, seat, UUID.nameUUIDFromBytes("VIP".getBytes()));
			if (!"PENDING_PAYMENT".equals(status) && !"EXPIRED".equals(status) && !"CANCELLED".equals(status)) {
				jdbc.update("update booking.seat_inventory set status = 'SOLD', booking_id = ? where event_id = ? "
						+ "and seat_id = ?", bookingId, event, seat);
			}
		}
		return bookingId;
	}

	/** Vé VALID cho mọi ghế của booking, như Ticket đã phát. */
	private List<UUID> issueTickets(UUID booking, UUID event) {
		List<UUID> tickets = new ArrayList<>();
		int number = 0;
		for (UUID seat : jdbc.queryForList("select seat_id from booking.booking_items where booking_id = ? "
				+ "order by seat_id", UUID.class, booking)) {
			UUID ticket = UUID.randomUUID();
			tickets.add(ticket);
			jdbc.update("""
					insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token,
					    key_id, event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name,
					    issued_at)
					values (?, ?, ?, ?, ?, 'VALID', 'token', 'k1', 'Sự kiện thử', ?, 'Sài Gòn Arena', 'A', 'A', ?, 'VIP', ?)
					""", ticket, booking, event, seat, customer, Timestamp.from(NOW.plus(Duration.ofDays(30))),
					++number, Timestamp.from(NOW));
		}
		return tickets;
	}

	private BookingConfirmed confirmed(UUID booking, UUID event, int seatCount) {
		List<BookingConfirmed.Seat> seats = new ArrayList<>();
		int number = 0;
		for (UUID seat : jdbc.queryForList("select seat_id from booking.booking_items where booking_id = ? "
				+ "order by seat_id", UUID.class, booking)) {
			seats.add(new BookingConfirmed.Seat(seat, "A", "A", ++number, UUID.nameUUIDFromBytes("VIP".getBytes()),
					"VIP", 1_000_000L));
		}
		assertThat(seats).hasSize(seatCount);
		return new BookingConfirmed(UUID.randomUUID(), NOW, booking, customer, event, "Sự kiện thử",
				NOW.plus(Duration.ofDays(30)), "Sài Gòn Arena", "customer@tickethub.local", 1_000_000L * seatCount, "VND",
				seats);
	}

}
