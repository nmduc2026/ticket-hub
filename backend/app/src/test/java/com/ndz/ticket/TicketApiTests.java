package com.ndz.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.ticket.event.TicketIssued;
import com.ndz.ticket.repository.SigningKeyRepository;

/**
 * Phát vé và "Vé của tôi" (plan Sprint 3 việc 7; AC-9; overview.md §6.4, §7.5, §13.4; ADR 0024): mỗi ghế một vé, QR là
 * JWT ES256 xác minh được bằng khoá công khai theo {@code kid}, phát một lần cho cả booking, chỉ thấy vé của mình.
 */
@IntegrationTest
class TicketApiTests {

	private static final Instant STARTS_AT = Instant.parse("2026-12-01T12:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EventRecorder events;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	SigningKeyRepository keys;

	@Autowired
	ECKey signingKey;

	private final UUID customer = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		events.clear();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from ticket.tickets");
		jdbc.update("delete from ticket.processed_events");
	}

	// ---- Phát vé ----

	@Test
	@DisplayName("AC-2: BookingConfirmed có 2 ghế thì có đúng 2 vé VALID, mỗi ghế một vé, phát TicketIssued một lần")
	void everySeatGetsOneValidTicketAndTheBookingGetsOneTicketIssued() {
		BookingConfirmed confirmed = confirmed(customer, 2);

		publish(confirmed);

		awaitTickets(confirmed.bookingId(), 2);
		assertThat(jdbc.queryForList("select status from ticket.tickets where booking_id = ?", String.class,
				confirmed.bookingId())).containsOnly("VALID");
		assertThat(jdbc.queryForList("select seat_id from ticket.tickets where booking_id = ?", UUID.class,
				confirmed.bookingId())).containsExactlyInAnyOrderElementsOf(
						confirmed.seats().stream().map(BookingConfirmed.Seat::seatId).toList());
		List<TicketIssued> issued = events.of(TicketIssued.class);
		assertThat(issued).hasSize(1);
		assertThat(issued.get(0).bookingId()).isEqualTo(confirmed.bookingId());
		assertThat(issued.get(0).tickets()).hasSize(2);
		assertThat(issued.get(0).contactEmail()).isEqualTo("customer@tickethub.local");
		assertThat(issued.get(0).eventTitle()).isEqualTo("Sự kiện thử");
		assertThat(issued.get(0).venueName()).isEqualTo("Sài Gòn Arena");
	}

	@Test
	@DisplayName("§8.3: nhận BookingConfirmed hai lần không phát vé trùng và không phát TicketIssued lần hai")
	void theSameBookingConfirmedTwiceIssuesOnce() {
		BookingConfirmed confirmed = confirmed(customer, 2);

		publish(confirmed);
		publish(confirmed);
		publish(new BookingConfirmed(UUID.randomUUID(), confirmed.occurredAt(), confirmed.bookingId(),
				confirmed.userId(), confirmed.eventId(), confirmed.eventTitle(), confirmed.startsAt(),
				confirmed.venueName(), confirmed.contactEmail(), confirmed.totalMinor(), confirmed.currency(),
				confirmed.seats()));

		awaitTickets(confirmed.bookingId(), 2);
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
			assertThat(count("select count(*) from ticket.tickets where booking_id = ?", confirmed.bookingId()))
					.isEqualTo(2);
			assertThat(events.of(TicketIssued.class)).hasSize(1);
		});
	}

	// ---- QR (AC-9) ----

	@Test
	@DisplayName("AC-9: QR là JWT ES256 với ticketId, eventId, seat, kid; verify được bằng khoá công khai lấy theo kid")
	void qrTokenVerifiesWithThePublicKeyLookedUpByKid() throws Exception {
		BookingConfirmed confirmed = confirmed(customer, 1);
		publish(confirmed);
		awaitTickets(confirmed.bookingId(), 1);
		var ticket = jdbc.queryForMap("select id, qr_token, key_id from ticket.tickets where booking_id = ?",
				confirmed.bookingId());

		SignedJWT jwt = SignedJWT.parse((String) ticket.get("qr_token"));
		String kid = jwt.getHeader().getKeyID();
		ECKey publicKey = ECKey.parse(keys.findPublicKey(kid).orElseThrow());

		assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
		assertThat(kid).isEqualTo(ticket.get("key_id")).isEqualTo(signingKey.getKeyID());
		assertThat(publicKey.isPrivate()).isFalse();
		assertThat(jwt.verify(new ECDSAVerifier(publicKey))).isTrue();
		assertThat(jwt.getJWTClaimsSet().getClaims()).containsOnlyKeys("ticketId", "eventId", "seat", "kid");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("ticketId")).isEqualTo(ticket.get("id").toString());
		assertThat(jwt.getJWTClaimsSet().getStringClaim("eventId")).isEqualTo(confirmed.eventId().toString());
		assertThat(jwt.getJWTClaimsSet().getStringClaim("seat")).isEqualTo("A-B-1");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("kid")).isEqualTo(kid);
	}

	@Test
	@DisplayName("§13.4 Vé giả: sửa nội dung hoặc ký bằng khoá khác thì không verify được")
	void aTamperedOrForeignSignedTokenDoesNotVerify() throws Exception {
		BookingConfirmed confirmed = confirmed(customer, 1);
		publish(confirmed);
		awaitTickets(confirmed.bookingId(), 1);
		String token = jdbc.queryForObject("select qr_token from ticket.tickets where booking_id = ?", String.class,
				confirmed.bookingId());
		ECKey publicKey = signingKey.toPublicJWK();

		String[] parts = token.split("\\.");
		String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("A-B-1", "A-B-2")
						.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		SignedJWT tampered = SignedJWT.parse(parts[0] + "." + forgedPayload + "." + parts[2]);
		ECKey stranger = new ECKeyGenerator(Curve.P_256).keyID(signingKey.getKeyID()).generate();
		SignedJWT foreign = new SignedJWT(tampered.getHeader(), tampered.getJWTClaimsSet());
		foreign.sign(new com.nimbusds.jose.crypto.ECDSASigner(stranger));

		assertThat(SignedJWT.parse(token).verify(new ECDSAVerifier(publicKey))).isTrue();
		assertThat(tampered.verify(new ECDSAVerifier(publicKey))).isFalse();
		assertThat(foreign.verify(new ECDSAVerifier(publicKey))).isFalse();
	}

	@Test
	@DisplayName("Xoay vòng khoá: khoá mới ACTIVE, khoá cũ RETIRED nhưng vẫn tra được để xác minh vé đã phát")
	void rotatingTheKeyKeepsTheOldPublicKeyForVerification() throws JOSEException {
		String oldKid = signingKey.getKeyID();
		ECKey next = new ECKeyGenerator(Curve.P_256).keyID("rotated-" + UUID.randomUUID()).generate();
		try {
			keys.activate(next.getKeyID(), next.toPublicJWK().toJSONString());

			assertThat(jdbc.queryForObject("select status from ticket.signing_keys where key_id = ?", String.class,
					oldKid)).isEqualTo("RETIRED");
			assertThat(jdbc.queryForObject("select status from ticket.signing_keys where key_id = ?", String.class,
					next.getKeyID())).isEqualTo("ACTIVE");
			assertThat(keys.findPublicKey(oldKid)).isPresent();
			assertThat(jdbc.queryForObject("select count(*) from ticket.signing_keys where status = 'ACTIVE'",
					Integer.class)).isEqualTo(1);
		}
		finally {
			keys.activate(oldKid, signingKey.toPublicJWK().toJSONString());
			jdbc.update("delete from ticket.signing_keys where key_id = ?", next.getKeyID());
		}
	}

	// ---- Chốt chặn oversell (BR-01) ----

	@Test
	@DisplayName("BR-01: DB không cho hai vé chưa thu hồi cùng một ghế; thu hồi vé cũ rồi thì phát được vé mới")
	void theDatabaseRefusesTwoLiveTicketsForOneSeat() {
		BookingConfirmed first = confirmed(customer, 1);
		publish(first);
		awaitTickets(first.bookingId(), 1);
		UUID seat = first.seats().get(0).seatId();

		assertThatThrownBy(() -> insertTicket(UUID.randomUUID(), first.eventId(), seat, "VALID"))
				.isInstanceOf(DuplicateKeyException.class).hasMessageContaining("uq_ticket_seat_active");

		jdbc.update("update ticket.tickets set status = 'REVOKED' where booking_id = ?", first.bookingId());
		insertTicket(UUID.randomUUID(), first.eventId(), seat, "VALID");
		assertThat(count("select count(*) from ticket.tickets where seat_id = ?", seat)).isEqualTo(2);
	}

	// ---- GET /me/tickets (AC-9) ----

	@Test
	@DisplayName("AC-9: GET /me/tickets chỉ trả vé của người đang đăng nhập")
	void iOnlySeeMyOwnTickets() throws Exception {
		BookingConfirmed mine = confirmed(customer, 2);
		BookingConfirmed someoneElses = confirmed(UUID.randomUUID(), 3);
		publish(mine);
		publish(someoneElses);
		awaitTickets(mine.bookingId(), 2);
		awaitTickets(someoneElses.bookingId(), 3);

		mockMvc.perform(get("/api/v1/me/tickets").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].eventTitle").value("Sự kiện thử"))
				.andExpect(jsonPath("$[0].venueName").value("Sài Gòn Arena"))
				.andExpect(jsonPath("$[0].status").value("VALID")).andExpect(jsonPath("$[0].qrToken").isNotEmpty())
				.andExpect(jsonPath("$[0].seat.sectionCode").value("A")).andExpect(jsonPath("$[0].seat.row").value("B"))
				.andExpect(jsonPath("$[0].ticketId").isNotEmpty());
		mockMvc.perform(get("/api/v1/me/tickets").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(UUID.randomUUID()).bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void paginationLimitsTheNumberOfTickets() throws Exception {
		BookingConfirmed mine = confirmed(customer, 3);
		publish(mine);
		awaitTickets(mine.bookingId(), 3);

		mockMvc.perform(get("/api/v1/me/tickets?page=0&size=2").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/v1/me/tickets?page=1&size=2").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void withoutLoginIs401AndWithoutTheCustomerRoleIs403() throws Exception {
		mockMvc.perform(get("/api/v1/me/tickets")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/tickets").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).roles("ORGANIZER").bearer())).andExpect(status().isForbidden());
	}

	// ---- helpers ----

	private void publish(BookingConfirmed message) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(message));
	}

	private void awaitTickets(UUID booking, int expected) {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				count("select count(*) from ticket.tickets where booking_id = ?", booking)).isEqualTo(expected));
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private static BookingConfirmed confirmed(UUID user, int seats) {
		UUID event = UUID.randomUUID();
		List<BookingConfirmed.Seat> list = new java.util.ArrayList<>();
		for (int i = 1; i <= seats; i++) {
			list.add(new BookingConfirmed.Seat(UUID.randomUUID(), "A", "B", i, UUID.randomUUID(), "VIP", 1_000_000));
		}
		return new BookingConfirmed(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), user, event, "Sự kiện thử",
				STARTS_AT, "Sài Gòn Arena", "customer@tickethub.local", 1_000_000L * seats, "VND", list);
	}

	private void insertTicket(UUID id, UUID event, UUID seat, String status) {
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, 'x', 'k', 'Sự kiện', ?, 'Nơi', 'A', 'B', 9, 'VIP', ?)
				""", id, UUID.randomUUID(), event, seat, UUID.randomUUID(), status, Timestamp.from(STARTS_AT),
				Timestamp.from(Instant.now()));
	}

}
