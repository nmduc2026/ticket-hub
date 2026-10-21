package com.ndz.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.MutableClock;
import com.ndz.backend.TestTokens;
import com.ndz.backend.booking.event.BookingCreated;

/**
 * Giữ ghế {@code POST /bookings} (plan Sprint 2 việc 7; BR-01, BR-02, BR-03; overview.md §7.2, §10; ADR 0020, 0025):
 * quy tắc kiểm trước khi khoá, giữ ghế nguyên tử, tranh chấp đồng thời không bán trùng, idempotency.
 */
@IntegrationTest
@RecordApplicationEvents
class BookingHoldTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

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

	// ---- Giữ ghế thành công ----

	@Test
	@DisplayName("BR-03: giữ ghế thành công trả 201, hạn giữ 10 phút, ghế chuyển HELD và gắn đúng booking")
	void holdingAvailableSeatsCreatesAPendingBookingValidFor10Minutes() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 2, 1_000_000);
		UUID user = UUID.randomUUID();

		String body = hold(user, event, seats).andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalMinor").value(2_000_000)).andExpect(jsonPath("$.currency").value("VND"))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-01T03:10:00Z")).andReturn().getResponse()
				.getContentAsString();

		UUID bookingId = UUID.fromString(JsonPath.read(body, "$.bookingId"));
		assertThat(jdbc.queryForObject("select status from booking.bookings where id = ?", String.class, bookingId))
				.isEqualTo("PENDING_PAYMENT");
		assertThat(jdbc.queryForObject("select count(*) from booking.seat_inventory where event_id = ? "
				+ "and status = 'HELD' and booking_id = ?", Integer.class, event, bookingId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from booking.booking_items where booking_id = ?",
				Integer.class, bookingId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select contact_email from booking.bookings where id = ?", String.class,
				bookingId)).isEqualTo("customer@tickethub.local");
		assertThat(applicationEvents.stream(BookingCreated.class).filter(e -> e.bookingId().equals(bookingId)).count())
				.isEqualTo(1);
	}

	@Test
	void ownerCanReadTheBookingButAnotherUserGets404() throws Exception {
		UUID event = onSaleEvent(6);
		UUID user = UUID.randomUUID();
		UUID bookingId = bookingIdOf(hold(user, event, seats(event, 2, 1_000_000)));

		mockMvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
				.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalMinor").value(2_000_000))
				.andExpect(jsonPath("$.items[0].tierName").value("VIP"));
		mockMvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION,
				bearer(UUID.randomUUID()))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
	}

	@Test
	void requestWithoutTokenIs401AndNonCustomerRoleIs403() throws Exception {
		UUID event = onSaleEvent(6);
		String json = body(event, seats(event, 1, 1000));

		mockMvc.perform(post("/api/v1/bookings").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
				.content(json)).andExpect(status().isUnauthorized());
		String noRole = TestTokens.token().subject(UUID.randomUUID()).roles("offline_access").bearer();
		mockMvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, noRole)
				.header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isForbidden());
	}

	// ---- Quy tắc kiểm trước khi khoá ghế ----

	@Test
	void seatThatIsAlreadyHeldIsRejectedAndNothingElseGetsHeld() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 3, 1_000_000);
		hold(UUID.randomUUID(), event, List.of(seats.get(1))).andExpect(status().isCreated());

		hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(1); // chỉ ghế của booking đầu; hai ghế còn lại không bị giữ dở
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
	}

	@Test
	void seatsThatDoNotExistInTheInventoryAreUnavailable() throws Exception {
		UUID event = onSaleEvent(6);
		UUID otherEvent = onSaleEvent(6);
		List<UUID> ofOtherEvent = seats(otherEvent, 1, 1000);

		hold(UUID.randomUUID(), event, List.of(UUID.randomUUID())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
		hold(UUID.randomUUID(), event, ofOtherEvent).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
	}

	@Test
	@DisplayName("BR-02: đúng 6 ghế được, 7 ghế bị MAX_SEATS_EXCEEDED")
	void aBookingHoldsAtMostSixSeats() throws Exception {
		UUID event = onSaleEvent(10);
		List<UUID> seats = seats(event, 7, 1000);

		hold(UUID.randomUUID(), event, seats).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MAX_SEATS_EXCEEDED"));
		hold(UUID.randomUUID(), event, seats.subList(0, 6)).andExpect(status().isCreated());
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(6);
	}

	@Test
	@DisplayName("BR-02: mỗi user chỉ 1 booking đang chờ thanh toán cho mỗi sự kiện")
	void secondPendingBookingForTheSameEventIsRejected() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 4, 1000);
		UUID user = UUID.randomUUID();
		hold(user, event, seats.subList(0, 2)).andExpect(status().isCreated());

		hold(user, event, seats.subList(2, 4)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PENDING_BOOKING_EXISTS"));

		// Sự kiện khác thì được.
		UUID other = onSaleEvent(6);
		hold(user, other, seats(other, 1, 1000)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("BR-02: tổng vé (chờ thanh toán + đã xác nhận) không vượt max_tickets_per_user, biên đúng bằng giới hạn")
	void totalTicketsPerUserCountsPendingAndConfirmedBookings() throws Exception {
		UUID event = onSaleEvent(4);
		List<UUID> seats = seats(event, 6, 1000);
		UUID user = UUID.randomUUID();
		// Đã mua 3 vé (CONFIRMED) trước đó.
		UUID confirmed = insertBooking(user, event, "CONFIRMED", seats.subList(0, 3));

		hold(user, event, seats.subList(3, 5)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TICKET_LIMIT_EXCEEDED")); // 3 + 2 > 4
		hold(user, event, seats.subList(3, 4)).andExpect(status().isCreated()); // 3 + 1 = 4: đúng giới hạn

		assertThat(confirmed).isNotNull();
	}

	@Test
	void expiredBookingsDoNotCountTowardsTheLimit() throws Exception {
		UUID event = onSaleEvent(2);
		List<UUID> seats = seats(event, 4, 1000);
		UUID user = UUID.randomUUID();
		insertBooking(user, event, "EXPIRED", seats.subList(0, 2));
		insertBooking(user, event, "CANCELLED", seats.subList(2, 3));

		hold(user, event, seats.subList(3, 4)).andExpect(status().isCreated());
	}

	@Test
	void eventThatIsNotOnSaleRejectsHolds() throws Exception {
		for (String notSelling : new String[] { "PUBLISHED", "SUSPENDED", "ENDED", "CANCELLED" }) {
			UUID event = onSaleEvent(6);
			jdbc.update("update booking.event_snapshots set status = ? where event_id = ?", notSelling, event);
			List<UUID> seats = seats(event, 1, 1000);

			hold(UUID.randomUUID(), event, seats).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("EVENT_NOT_ON_SALE"));
			assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status <> 'AVAILABLE'",
					event)).isZero();
		}
		hold(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	void invalidRequestsAreRejected() throws Exception {
		UUID event = onSaleEvent(6);
		UUID seat = seats(event, 1, 1000).get(0);

		hold(UUID.randomUUID(), event, List.of()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		hold(UUID.randomUUID(), event, List.of(seat, seat)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DUPLICATE_SEATS"));
		String noEmail = TestTokens.token().subject(UUID.randomUUID()).email(null).bearer();
		mockMvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, noEmail)
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content(body(event, List.of(seat)))).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
	}

	// ---- Idempotency (AC-7, §6.7) ----

	@Test
	@DisplayName("§6.7: gửi lại cùng Idempotency-Key và body trả cùng response, không tạo booking mới")
	void sameKeyAndBodyReturnsTheSameBookingWithoutCreatingAnother() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 2, 1_000_000);
		UUID user = UUID.randomUUID();
		String key = UUID.randomUUID().toString();

		MockHttpServletResponse first = hold(user, event, seats, key).andExpect(status().isCreated()).andReturn()
				.getResponse();
		MockHttpServletResponse retry = hold(user, event, seats, key).andExpect(status().isCreated()).andReturn()
				.getResponse();

		assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(retry.getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
	}

	@Test
	void sameKeyWithADifferentBodyIs422() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 3, 1000);
		UUID user = UUID.randomUUID();
		String key = UUID.randomUUID().toString();
		hold(user, event, seats.subList(0, 1), key).andExpect(status().isCreated());

		hold(user, event, seats.subList(1, 2), key).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
	}

	@Test
	void retryAfterAFailedHoldReplaysTheSameFailure() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 1, 1000);
		hold(UUID.randomUUID(), event, seats).andExpect(status().isCreated());
		UUID user = UUID.randomUUID();
		String key = UUID.randomUUID().toString();

		hold(user, event, seats, key).andExpect(status().isConflict());
		hold(user, event, seats, key).andExpect(status().isConflict()).andExpect(jsonPath("$.code")
				.value("SEAT_UNAVAILABLE"));
	}

	// ---- BR-01: tranh chấp đồng thời ----

	@Test
	@DisplayName("BR-01: 50 luồng cùng giữ 1 ghế thì đúng 1 thành công, 49 nhận SEAT_UNAVAILABLE")
	void fiftyConcurrentRequestsForOneSeatYieldExactlyOneWinner() throws Exception {
		UUID event = onSaleEvent(6);
		UUID seat = seats(event, 1, 1_000_000).get(0);
		int contenders = 50;

		List<MockHttpServletResponse> responses = runConcurrently(contenders,
				index -> hold(UUID.randomUUID(), event, List.of(seat)).andReturn().getResponse());

		assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.getStatus() == 409).hasSize(contenders - 1)
				.allSatisfy(r -> assertThat(r.getContentAsString()).contains("SEAT_UNAVAILABLE"));
		assertThat(responses).filteredOn(r -> r.getStatus() >= 500).isEmpty();
		assertNoSeatIsHeldByTwoBookings(event);
		assertThat(count("select count(*) from booking.bookings where event_id = ?", event)).isEqualTo(1);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("BR-01: hai booking giữ tập ghế chồng nhau theo thứ tự ngược nhau không deadlock treo, không bán trùng")
	void overlappingSeatSetsInOppositeOrderNeitherDeadlockNorDoubleHold() throws Exception {
		UUID event = onSaleEvent(6);
		int pairs = 25;
		List<UUID> seats = seats(event, pairs * 2, 1000);

		List<MockHttpServletResponse> responses = runConcurrently(pairs * 2, index -> {
			int pair = index / 2;
			UUID first = seats.get(pair * 2);
			UUID second = seats.get(pair * 2 + 1);
			List<UUID> order = index % 2 == 0 ? List.of(first, second) : List.of(second, first);
			return hold(UUID.randomUUID(), event, order).andReturn().getResponse();
		});

		assertThat(responses).filteredOn(r -> r.getStatus() >= 500).isEmpty();
		assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(pairs);
		assertThat(responses).filteredOn(r -> r.getStatus() == 409).hasSize(pairs);
		assertNoSeatIsHeldByTwoBookings(event);
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(pairs * 2);
	}

	@Test
	void differentUsersHoldingDifferentSeatsAtTheSameTimeAllSucceed() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 30, 1000);

		List<MockHttpServletResponse> responses = runConcurrently(30,
				index -> hold(UUID.randomUUID(), event, List.of(seats.get(index))).andReturn().getResponse());

		assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
		assertNoSeatIsHeldByTwoBookings(event);
	}

	@Test
	void concurrentRequestsOfTheSameUserCreateOnlyOnePendingBooking() throws Exception {
		UUID event = onSaleEvent(6);
		List<UUID> seats = seats(event, 10, 1000);
		UUID user = UUID.randomUUID();

		List<MockHttpServletResponse> responses = runConcurrently(10,
				index -> hold(user, event, List.of(seats.get(index))).andReturn().getResponse());

		assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.getStatus() == 409).hasSize(9)
				.allSatisfy(r -> assertThat(r.getContentAsString()).contains("PENDING_BOOKING_EXISTS"));
		assertThat(count("select count(*) from booking.seat_inventory where event_id = ? and status = 'HELD'", event))
				.isEqualTo(1);
	}

	// ---- helpers ----

	/** Chạy {@code count} request cùng lúc: mọi luồng chờ tại một hàng rào rồi xuất phát đồng thời. */
	private List<MockHttpServletResponse> runConcurrently(int count, RequestAction action) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				int index = i;
				Callable<MockHttpServletResponse> task = () -> {
					ready.countDown();
					go.await();
					return action.run(index);
				};
				futures.add(pool.submit(task));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<MockHttpServletResponse> responses = new ArrayList<>();
			for (Future<MockHttpServletResponse> future : futures) {
				responses.add(future.get(60, TimeUnit.SECONDS));
			}
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	@FunctionalInterface
	private interface RequestAction {

		MockHttpServletResponse run(int index) throws Exception;

	}

	/** Bất biến BR-01 (overview.md §10): không ghế nào thuộc hơn một booking còn hiệu lực. */
	private void assertNoSeatIsHeldByTwoBookings(UUID event) {
		List<Integer> duplicates = jdbc.queryForList("""
				SELECT count(*) FROM booking.booking_items i JOIN booking.bookings b ON b.id = i.booking_id
				WHERE b.event_id = ? AND b.status IN ('PENDING_PAYMENT', 'CONFIRMED')
				GROUP BY i.seat_id HAVING count(*) > 1
				""", Integer.class, event);
		assertThat(duplicates).isEmpty();
		// Mỗi ghế đang giữ phải khớp đúng một dòng booking_items của booking đó.
		assertThat(count("""
				SELECT count(*) FROM booking.seat_inventory s
				WHERE s.event_id = ? AND s.status = 'HELD'
				  AND NOT EXISTS (SELECT 1 FROM booking.booking_items i
				                  WHERE i.booking_id = s.booking_id AND i.seat_id = s.seat_id)
				""", event)).isZero();
	}

	private ResultActions hold(UUID user, UUID event, List<UUID> seats) throws Exception {
		return hold(user, event, seats, UUID.randomUUID().toString());
	}

	private ResultActions hold(UUID user, UUID event, List<UUID> seats, String idempotencyKey) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
				.content(body(event, seats));
		return mockMvc.perform(request);
	}

	private static String body(UUID event, List<UUID> seats) {
		String ids = seats.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return "{\"eventId\":\"%s\",\"seatIds\":[%s]}".formatted(event, ids);
	}

	private static String bearer(UUID user) {
		return TestTokens.token().subject(user).bearer();
	}

	private static UUID bookingIdOf(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.bookingId"));
	}

	private UUID onSaleEvent(int maxTicketsPerUser) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into booking.event_snapshots (event_id, organizer_id, title, venue_id, venue_name, city_id,
				    city_name, seat_map_id, starts_at, ends_at, sale_starts_at, status, max_tickets_per_user,
				    platform_fee_bps, last_event_at)
				values (?, ?, 'Sự kiện thử', ?, 'Sài Gòn Arena', ?, 'Thành phố Hồ Chí Minh', ?, ?, ?, ?, 'ON_SALE', ?,
				    500, ?)
				""", id, TestTokens.ORGANIZER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Timestamp.from(NOW.plusSeconds(30 * 86_400)), Timestamp.from(NOW.plusSeconds(31 * 86_400)),
				Timestamp.from(NOW.minusSeconds(86_400)), maxTicketsPerUser, Timestamp.from(NOW));
		return id;
	}

	private List<UUID> seats(UUID event, int count, long priceMinor) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(UUID.randomUUID());
		}
		ids.sort(Comparator.naturalOrder());
		for (int i = 0; i < count; i++) {
			jdbc.update("""
					insert into booking.seat_inventory (event_id, seat_id, tier_id, tier_name, section_code, row_label,
					    seat_number, price_minor)
					values (?, ?, ?, 'VIP', 'A', 'A', ?, ?)
					""", event, ids.get(i), UUID.nameUUIDFromBytes("VIP".getBytes()), i + 1, priceMinor);
		}
		return ids;
	}

	/** Booking dựng sẵn ở trạng thái {@code status}; ghế gắn theo trạng thái tương ứng. */
	private UUID insertBooking(UUID user, UUID event, String status, List<UUID> seatIds) {
		UUID bookingId = UUID.randomUUID();
		jdbc.update("""
				insert into booking.bookings (id, user_id, event_id, status, total_minor, expires_at, contact_email)
				values (?, ?, ?, ?, ?, ?, 'a@b.c')
				""", bookingId, user, event, status, seatIds.size() * 1000L, Timestamp.from(NOW.plusSeconds(600)));
		for (UUID seat : seatIds) {
			jdbc.update("""
					insert into booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
					values (?, ?, ?, ?, 1000)
					""", bookingId, event, seat, UUID.nameUUIDFromBytes("VIP".getBytes()));
			if ("CONFIRMED".equals(status)) {
				jdbc.update("update booking.seat_inventory set status = 'SOLD', booking_id = ? where event_id = ? "
						+ "and seat_id = ?", bookingId, event, seat);
			}
			else if ("PENDING_PAYMENT".equals(status)) {
				jdbc.update("update booking.seat_inventory set status = 'HELD', booking_id = ? where event_id = ? "
						+ "and seat_id = ?", bookingId, event, seat);
			}
		}
		return bookingId;
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

}
