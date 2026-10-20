package com.ndz.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.MutableClock;
import com.ndz.backend.TestTokens;
import com.ndz.backend.booking.events.BookingCancelled;
import com.ndz.backend.booking.events.BookingExpired;
import com.ndz.backend.booking.internal.application.BookingExpiryService;

/**
 * Hết hạn giữ ghế và huỷ booking (plan Sprint 2 việc 8; BR-03, BR-12; overview.md §7.3, §7.7): dùng {@code Clock}
 * điều khiển được để kiểm hạn 10 phút và ân hạn 5 phút mà không phải chờ.
 */
@IntegrationTest
@RecordApplicationEvents
class BookingExpiryAndCancelTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant HOLD_ENDS = NOW.plusSeconds(600);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	BookingExpiryService expiry;

	@Autowired
	ApplicationEvents applicationEvents;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from booking.booking_items");
		jdbc.update("delete from booking.seat_inventory");
		jdbc.update("delete from booking.bookings");
		jdbc.update("delete from booking.event_snapshots");
		jdbc.update("delete from shared.idempotency_keys");
	}

	// ---- BR-03: hết hạn ----

	@Test
	@DisplayName("BR-03: quá 10 phút chưa thanh toán thì booking EXPIRED, ghế về AVAILABLE và người khác giữ được")
	void bookingExpiresAfterTenMinutesAndTheSeatsBecomeAvailableAgain() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID bookingId = hold(UUID.randomUUID(), event, seats);

		clock.setInstant(HOLD_ENDS.minusSeconds(1));
		assertThat(expiry.expireDueBookings()).isZero();
		assertThat(statusOf(bookingId)).isEqualTo("PENDING_PAYMENT");

		clock.setInstant(HOLD_ENDS.plusSeconds(1));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);

		assertThat(statusOf(bookingId)).isEqualTo("EXPIRED");
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE' "
				+ "and booking_id is null", event)).isEqualTo(2);
		assertThat(applicationEvents.stream(BookingExpired.class).filter(e -> e.bookingId().equals(bookingId))
				.count()).isEqualTo(1);
		hold(UUID.randomUUID(), event, seats); // người khác giữ lại được đúng các ghế đó
	}

	@Test
	void bookingExpiresExactlyAtItsDeadline() throws Exception {
		UUID event = onSaleEvent();
		UUID bookingId = hold(UUID.randomUUID(), event, seats(event, 1));

		clock.setInstant(HOLD_ENDS);
		assertThat(expiry.expireDueBookings()).isEqualTo(1);

		assertThat(statusOf(bookingId)).isEqualTo("EXPIRED");
	}

	@Test
	@DisplayName("BR-03: đã bắt đầu thanh toán thì được ân hạn thêm 5 phút rồi mới hết hạn")
	void bookingThatStartedPaymentGetsFiveMoreMinutes() throws Exception {
		UUID event = onSaleEvent();
		UUID bookingId = hold(UUID.randomUUID(), event, seats(event, 1));
		jdbc.update("update booking.bookings set payment_started_at = ? where id = ?",
				Timestamp.from(NOW.plusSeconds(500)), bookingId);

		clock.setInstant(HOLD_ENDS.plusSeconds(1)); // qua hạn gốc nhưng còn trong ân hạn
		assertThat(expiry.expireDueBookings()).isZero();
		clock.setInstant(HOLD_ENDS.plusSeconds(299));
		assertThat(expiry.expireDueBookings()).isZero();
		assertThat(statusOf(bookingId)).isEqualTo("PENDING_PAYMENT");

		clock.setInstant(HOLD_ENDS.plusSeconds(300));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);
		assertThat(statusOf(bookingId)).isEqualTo("EXPIRED");
	}

	@Test
	void onlyPendingBookingsExpireAndRunningTheJobAgainChangesNothing() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 3);
		UUID pending = hold(UUID.randomUUID(), event, seats.subList(0, 1));
		UUID confirmed = insertBooking(event, "CONFIRMED", seats.get(1));
		UUID cancelled = insertBooking(event, "CANCELLED", seats.get(2));

		clock.setInstant(HOLD_ENDS.plusSeconds(3600));
		assertThat(expiry.expireDueBookings()).isEqualTo(1);
		assertThat(expiry.expireDueBookings()).isZero();

		assertThat(statusOf(pending)).isEqualTo("EXPIRED");
		assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
		assertThat(statusOf(cancelled)).isEqualTo("CANCELLED");
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				confirmed)).isEqualTo(1); // ghế đã bán không bị nhả
		assertThat(applicationEvents.stream(BookingExpired.class).count()).isEqualTo(1);
	}

	@Test
	void jobProcessesMoreThanOneBatch() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 130);
		for (UUID seat : seats) {
			insertBooking(event, "PENDING_PAYMENT", seat);
		}
		clock.setInstant(HOLD_ENDS.plusSeconds(60));

		assertThat(expiry.expireDueBookings()).isEqualTo(130);

		assertThat(count("select count(*) from booking.bookings where status = 'EXPIRED'")).isEqualTo(130);
		assertThat(count("select count(*) from booking.seat_inventory where status = 'AVAILABLE'")).isEqualTo(130);
	}

	// ---- BR-12: khách huỷ ----

	@Test
	@DisplayName("BR-12: khách huỷ booking chờ thanh toán thì CANCELLED và ghế về AVAILABLE ngay")
	void customerCancelsAPendingBookingAndSeatsAreFreedImmediately() throws Exception {
		UUID event = onSaleEvent();
		List<UUID> seats = seats(event, 2);
		UUID user = UUID.randomUUID();
		UUID bookingId = hold(user, event, seats);

		cancel(user, bookingId).andExpect(status().isNoContent());

		assertThat(statusOf(bookingId)).isEqualTo("CANCELLED");
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE' "
				+ "and booking_id is null", event)).isEqualTo(2);
		assertThat(applicationEvents.stream(BookingCancelled.class).filter(e -> e.bookingId().equals(bookingId))
				.count()).isEqualTo(1);
		hold(UUID.randomUUID(), event, seats); // ghế được giữ lại ngay
	}

	@Test
	void cancellingSomeoneElsesOrUnknownBookingIs404AndLeavesItAlone() throws Exception {
		UUID event = onSaleEvent();
		UUID owner = UUID.randomUUID();
		UUID bookingId = hold(owner, event, seats(event, 1));

		cancel(UUID.randomUUID(), bookingId).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
		cancel(owner, UUID.randomUUID()).andExpect(status().isNotFound());

		assertThat(statusOf(bookingId)).isEqualTo("PENDING_PAYMENT");
	}

	@Test
	void cancellingWithoutLoginIs401() throws Exception {
		mockMvc.perform(delete("/api/v1/bookings/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	void expiredCancelledOrConfirmedBookingsCannotBeCancelled() throws Exception {
		UUID event = onSaleEvent();
		UUID user = UUID.randomUUID();
		List<UUID> seats = seats(event, 3);
		UUID expired = insertBooking(user, event, "EXPIRED", seats.get(0));
		UUID cancelled = insertBooking(user, event, "CANCELLED", seats.get(1));
		UUID confirmed = insertBooking(user, event, "CONFIRMED", seats.get(2));

		for (UUID bookingId : List.of(expired, cancelled, confirmed)) {
			cancel(user, bookingId).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
		}
		assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
		assertThat(count("select count(*) from booking.seat_inventory where booking_id = ? and status = 'SOLD'",
				confirmed)).isEqualTo(1);
	}

	@Test
	void cancellingTwiceTheSecondTimeIs409() throws Exception {
		UUID event = onSaleEvent();
		UUID user = UUID.randomUUID();
		UUID bookingId = hold(user, event, seats(event, 1));

		cancel(user, bookingId).andExpect(status().isNoContent());
		cancel(user, bookingId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
	}

	// ---- Đồng thời ----

	@Test
	void manySimultaneousCancelsOfTheSameBookingSucceedExactlyOnce() throws Exception {
		UUID event = onSaleEvent();
		UUID user = UUID.randomUUID();
		UUID bookingId = hold(user, event, seats(event, 2));

		List<Integer> statuses = runConcurrently(30, index -> cancel(user, bookingId).andReturn().getResponse()
				.getStatus());

		assertThat(statuses).filteredOn(code -> code == 204).hasSize(1);
		assertThat(statuses).filteredOn(code -> code == 409).hasSize(29);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'AVAILABLE'",
				event)).isEqualTo(2);
	}

	@Test
	void cancelRacingWithTheExpiryJobEndsInExactlyOneFinalState() throws Exception {
		UUID event = onSaleEvent();
		int bookingsCount = 20;
		List<UUID> users = new ArrayList<>();
		List<UUID> bookingIds = new ArrayList<>();
		List<UUID> seats = seats(event, bookingsCount);
		for (int i = 0; i < bookingsCount; i++) {
			UUID user = UUID.randomUUID();
			users.add(user);
			bookingIds.add(hold(user, event, seats.subList(i, i + 1)));
		}
		clock.setInstant(HOLD_ENDS.plusSeconds(1)); // đã quá hạn: cả hai bên cùng muốn đổi trạng thái

		List<Integer> cancelCodes = runConcurrently(bookingsCount + 1, index -> index == bookingsCount
				? -expiry.expireDueBookings() : cancel(users.get(index), bookingIds.get(index)).andReturn()
						.getResponse().getStatus());

		int cancelled = (int) cancelCodes.subList(0, bookingsCount).stream().filter(code -> code == 204).count();
		int expiredByJob = -cancelCodes.get(bookingsCount);
		assertThat(cancelCodes.subList(0, bookingsCount)).allSatisfy(code -> assertThat(code).isIn(204, 409));
		assertThat(cancelled + expiredByJob).isEqualTo(bookingsCount); // mỗi booking đổi đúng một lần
		assertThat(count("select count(*) from booking.bookings where status = 'CANCELLED'")).isEqualTo(cancelled);
		assertThat(count("select count(*) from booking.bookings where status = 'EXPIRED'")).isEqualTo(expiredByJob);
		assertThat(count("select count(*) from booking.seat_inventory where status = 'AVAILABLE' "
				+ "and booking_id is null")).isEqualTo(bookingsCount);
	}

	// ---- helpers ----

	private interface Action<T> {

		T run(int index) throws Exception;

	}

	private <T> List<T> runConcurrently(int count, Action<T> action) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				int index = i;
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return action.run(index);
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private UUID hold(UUID user, UUID event, List<UUID> seats) throws Exception {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		ResultActions result = mockMvc.perform(post("/api/v1/bookings")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids)));
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.bookingId"));
	}

	private ResultActions cancel(UUID user, UUID bookingId) throws Exception {
		return mockMvc.perform(delete("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(user).bearer()));
	}

	private String statusOf(UUID bookingId) {
		return jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, bookingId);
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
					""", event, seat, UUID.nameUUIDFromBytes("VIP".getBytes()), i + 1);
		}
		return ids;
	}

	private UUID insertBooking(UUID event, String status, UUID seat) {
		return insertBooking(UUID.randomUUID(), event, status, seat);
	}

	private UUID insertBooking(UUID user, UUID event, String status, UUID seat) {
		UUID bookingId = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, 1000000, ?, 'a@b.c')
				""", bookingId, user, event, status, Timestamp.from(HOLD_ENDS));
		jdbc.update("""
				insert into booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
				values (?, ?, ?, ?, 1000000)
				""", bookingId, event, seat, UUID.nameUUIDFromBytes("VIP".getBytes()));
		if ("CONFIRMED".equals(status)) {
			jdbc.update("update booking.seat_inventory set status = 'SOLD', booking_id = ? where event_id = ? and "
					+ "seat_id = ?", bookingId, event, seat);
		}
		else if ("PENDING_PAYMENT".equals(status)) {
			jdbc.update("update booking.seat_inventory set status = 'HELD', booking_id = ? where event_id = ? and "
					+ "seat_id = ?", bookingId, event, seat);
		}
		return bookingId;
	}

}
