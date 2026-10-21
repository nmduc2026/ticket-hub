package com.ndz.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.TicketCheckedIn;
import com.ndz.ticket.repository.SigningKeyRepository;
import com.ndz.ticket.util.QrTokenSigner;

/**
 * Check-in (plan Sprint 4 việc 5; AC-7, AC-8; BR-09; overview.md §7.5, §11.2; ADR 0024): vé hợp lệ → {@code USED}, quét
 * lần hai / vé thu hồi bị từ chối, chỉ organizer của sự kiện, QR giả không qua được, xoay khoá theo {@code kid}; chỉ
 * nhận trong khung giờ của sự kiện và phát {@code TicketCheckedIn} (plan sprint dọn dẹp việc 9, AC-8).
 */
@IntegrationTest
@RecordApplicationEvents
class CheckInApiTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant STARTS_AT = NOW.plusSeconds(3600);
	private static final Instant ENDS_AT = STARTS_AT.plusSeconds(3 * 3600);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	QrTokenSigner signer;

	@Autowired
	SigningKeyRepository keys;

	@Autowired
	ECKey signingKey;

	@Autowired
	MutableClock clock;

	@Autowired
	ApplicationEvents applicationEvents;

	private UUID eventId;
	private final UUID customer = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();

	@BeforeEach
	void prepare() {
		cleanUp();
		eventId = UUID.randomUUID();
		jdbc.update("""
				insert into catalog.events (id, organizer_id, title, starts_at, ends_at)
				values (?, ?, 'Sự kiện check-in', ?, ?)
				""", eventId, TestTokens.ORGANIZER_ID, Timestamp.from(STARTS_AT), Timestamp.from(ENDS_AT));
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from ticket.check_ins");
		jdbc.update("delete from ticket.tickets");
		jdbc.update("delete from catalog.events where title = 'Sự kiện check-in'");
		jdbc.update("delete from ticket.signing_keys where key_id like 'rotated-%'");
		keys.activate(signingKey.getKeyID(), signingKey.toPublicJWK().toJSONString());
	}

	// ---- BR-09 ----

	@Test
	@DisplayName("AC-7: check-in vé hợp lệ → USED, ghi check_ins; quét lần 2 → 409 TICKET_ALREADY_USED")
	void aValidTicketChecksInOnceAndTheSecondScanIsRefused() throws Exception {
		Ticket ticket = ticket("VALID");

		checkIn(organizer(), ticket.token(), "A1").andExpect(status().isOk())
				.andExpect(jsonPath("$.ticketId").value(ticket.id().toString()))
				.andExpect(jsonPath("$.seat").value("A-B-7")).andExpect(jsonPath("$.status").value("USED"));
		checkIn(organizer(), ticket.token(), "A2").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TICKET_ALREADY_USED"));

		assertThat(statusOf(ticket.id())).isEqualTo("USED");
		assertThat(jdbc.queryForMap("select gate, scanned_by from ticket.check_ins where ticket_id = ?", ticket.id()))
				.containsEntry("gate", "A1").containsEntry("scanned_by", TestTokens.ORGANIZER_ID);
		assertThat(count("select count(*) from ticket.check_ins where ticket_id = ?", ticket.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-7: vé đã thu hồi → 409 TICKET_REVOKED và không ghi check_ins")
	void aRevokedTicketIsRefused() throws Exception {
		Ticket ticket = ticket("REVOKED");

		checkIn(organizer(), ticket.token(), "A1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TICKET_REVOKED"));

		assertThat(statusOf(ticket.id())).isEqualTo("REVOKED");
		assertThat(count("select count(*) from ticket.check_ins")).isZero();
	}

	@Test
	@DisplayName("BR-09: 20 thiết bị quét cùng một vé cùng lúc thì đúng một nơi vào được")
	void simultaneousScansOfOneTicketLetExactlyOneIn() throws Exception {
		Ticket ticket = ticket("VALID");
		ExecutorService pool = Executors.newFixedThreadPool(20);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				String gate = "G" + i;
				futures.add(pool.submit(() -> checkIn(organizer(), ticket.token(), gate).andReturn().getResponse()
						.getStatus()));
			}
			List<Integer> codes = new ArrayList<>();
			for (Future<Integer> future : futures) {
				codes.add(future.get(60, TimeUnit.SECONDS));
			}
			assertThat(codes).filteredOn(code -> code == 200).hasSize(1);
			assertThat(codes).filteredOn(code -> code == 409).hasSize(19);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(count("select count(*) from ticket.check_ins where ticket_id = ?", ticket.id())).isEqualTo(1);
	}

	// ---- BR-09: khung giờ check-in ----

	@Test
	@DisplayName("AC-8: đúng 3 giờ trước giờ diễn → nhận; sớm hơn 1 giây → 422 CHECKIN_NOT_OPEN, vé giữ VALID")
	void checkInOpensExactlyThreeHoursBeforeTheShow() throws Exception {
		Ticket early = ticket("VALID");
		Ticket onTime = ticket("VALID");

		clock.setInstant(STARTS_AT.minusSeconds(3 * 3600 + 1));
		checkIn(organizer(), early.token(), "A1").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CHECKIN_NOT_OPEN"));
		clock.setInstant(STARTS_AT.minusSeconds(3 * 3600));
		checkIn(organizer(), onTime.token(), "A1").andExpect(status().isOk());

		assertThat(statusOf(early.id())).isEqualTo("VALID");
		assertThat(statusOf(onTime.id())).isEqualTo("USED");
	}

	@Test
	@DisplayName("AC-8: đúng giờ kết thúc → nhận; sau giờ kết thúc → 422 EVENT_ENDED, không ghi check_ins")
	void checkInClosesAfterTheEventEnds() throws Exception {
		Ticket atEnd = ticket("VALID");
		Ticket late = ticket("VALID");

		clock.setInstant(ENDS_AT);
		checkIn(organizer(), atEnd.token(), "A1").andExpect(status().isOk());
		clock.setInstant(ENDS_AT.plusSeconds(1));
		checkIn(organizer(), late.token(), "A1").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EVENT_ENDED"));

		assertThat(statusOf(late.id())).isEqualTo("VALID");
		assertThat(count("select count(*) from ticket.check_ins where ticket_id = ?", late.id())).isZero();
	}

	@Test
	@DisplayName("Sự kiện chưa có giờ diễn (nháp) → 422 CHECKIN_NOT_OPEN")
	void anEventWithoutStartTimeIsNotOpen() throws Exception {
		Ticket ticket = ticket("VALID");
		jdbc.update("update catalog.events set starts_at = null, ends_at = null where id = ?", eventId);

		checkIn(organizer(), ticket.token(), "A1").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CHECKIN_NOT_OPEN"));
	}

	@Test
	@DisplayName("AC-8: check-in hợp lệ phát đúng 1 TicketCheckedIn có eventId, seatId; quét lần 2 không phát thêm")
	void aSuccessfulCheckInPublishesExactlyOneTicketCheckedIn() throws Exception {
		Ticket ticket = ticket("VALID");
		UUID seatId = jdbc.queryForObject("select seat_id from ticket.tickets where id = ?", UUID.class, ticket.id());

		checkIn(organizer(), ticket.token(), "A1").andExpect(status().isOk());
		checkIn(organizer(), ticket.token(), "A2").andExpect(status().isConflict());

		List<TicketCheckedIn> published = applicationEvents.stream(TicketCheckedIn.class).toList();
		assertThat(published).singleElement().satisfies(event -> {
			assertThat(event.ticketId()).isEqualTo(ticket.id());
			assertThat(event.eventId()).isEqualTo(eventId);
			assertThat(event.seatId()).isEqualTo(seatId);
			assertThat(event.gate()).isEqualTo("A1");
			assertThat(event.occurredAt()).isEqualTo(NOW);
		});
	}

	// ---- Quyền ----

	@Test
	@DisplayName("AC-7: organizer không sở hữu sự kiện → 403 NOT_EVENT_OWNER và vé giữ nguyên VALID")
	void anotherOrganizerCannotCheckInOrProbeTheTicket() throws Exception {
		Ticket ticket = ticket("VALID");
		Ticket revoked = ticket("REVOKED");
		String other = TestTokens.token().subject(stranger).roles("ORGANIZER").bearer();

		checkIn(other, ticket.token(), "A1").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_EVENT_OWNER"));
		// Không lộ trạng thái vé của sự kiện không phải của mình.
		checkIn(other, revoked.token(), "A1").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_EVENT_OWNER"));

		assertThat(statusOf(ticket.id())).isEqualTo("VALID");
	}

	@Test
	void customerAndAnonymousCannotCheckIn() throws Exception {
		Ticket ticket = ticket("VALID");

		checkIn(TestTokens.token().subject(customer).bearer(), ticket.token(), "A1")
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/checkins").contentType(MediaType.APPLICATION_JSON)
				.content("{\"qrToken\":\"x\",\"gate\":\"A1\"}")).andExpect(status().isUnauthorized());

		assertThat(statusOf(ticket.id())).isEqualTo("VALID");
	}

	// ---- QR giả ----

	@Test
	@DisplayName("AC-7: QR ký bằng khoá lạ, bị sửa nội dung, kid lạ hoặc không phải JWT → 404 TICKET_NOT_FOUND")
	void forgedTokensAreRefused() throws Exception {
		Ticket ticket = ticket("VALID");
		ECKey foreignKey = new ECKeyGenerator(Curve.P_256).keyID(signingKey.getKeyID()).generate();
		String signedByStranger = sign(foreignKey, ticket.id(), eventId);
		String unknownKid = sign(new ECKeyGenerator(Curve.P_256).keyID("no-such-key").generate(), ticket.id(), eventId);
		String[] parts = ticket.token().split("\\.");
		String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("A-B-7", "A-B-8")
						.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];

		for (String token : List.of(signedByStranger, unknownKid, tampered, "khong-phai-jwt", "a.b.c")) {
			checkIn(organizer(), token, "A1").andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
		}

		assertThat(statusOf(ticket.id())).isEqualTo("VALID");
		assertThat(count("select count(*) from ticket.check_ins")).isZero();
	}

	@Test
	@DisplayName("QR hợp lệ nhưng vé không có trong DB, hoặc eventId trong QR khác eventId của vé → 404")
	void unknownTicketOrMismatchedEventIsNotFound() throws Exception {
		Ticket ticket = ticket("VALID");
		String unknownTicket = sign(signingKey, UUID.randomUUID(), eventId);
		String wrongEvent = sign(signingKey, ticket.id(), UUID.randomUUID());

		checkIn(organizer(), unknownTicket, "A1").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
		checkIn(organizer(), wrongEvent, "A1").andExpect(status().isNotFound());

		assertThat(statusOf(ticket.id())).isEqualTo("VALID");
	}

	@Test
	void requestWithoutGateOrTokenIs400() throws Exception {
		mockMvc.perform(post("/api/v1/checkins").header(HttpHeaders.AUTHORIZATION, organizer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"\",\"gate\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	// ---- AC-8: xoay khoá ----

	@Test
	@DisplayName("AC-8: sau khi xoay khoá, vé ký bằng kid cũ vẫn check-in được; vé ký bằng kid mới cũng vậy")
	void ticketsSignedBeforeAndAfterKeyRotationBothCheckIn() throws Exception {
		Ticket old = ticket("VALID"); // ký bằng khoá hiện tại của app
		ECKey next = new ECKeyGenerator(Curve.P_256).keyID("rotated-" + UUID.randomUUID()).generate();
		keys.activate(next.getKeyID(), next.toPublicJWK().toJSONString());
		UUID newTicketId = insertTicket("VALID", sign(next, UUID.randomUUID(), eventId), next.getKeyID());
		String newToken = sign(next, newTicketId, eventId);

		checkIn(organizer(), old.token(), "A1").andExpect(status().isOk());
		checkIn(organizer(), newToken, "A1").andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select status from ticket.signing_keys where key_id = ?", String.class,
				signingKey.getKeyID())).isEqualTo("RETIRED");
		assertThat(jdbc.queryForObject("select status from ticket.signing_keys where key_id = ?", String.class,
				next.getKeyID())).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("GET /checkin/keys: JWKS gồm khoá ACTIVE và RETIRED, mỗi khoá có kid, kty, crv, x, y; không lộ khoá riêng")
	void keySetListsActiveAndRetiredKeysWithoutPrivateParts() throws Exception {
		ECKey next = new ECKeyGenerator(Curve.P_256).keyID("rotated-" + UUID.randomUUID()).generate();
		keys.activate(next.getKeyID(), next.toPublicJWK().toJSONString());

		String body = mockMvc.perform(get("/api/v1/checkin/keys").header(HttpHeaders.AUTHORIZATION, organizer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.keys[?(@.kid=='" + signingKey.getKeyID()
						+ "')].crv").value("P-256"))
				.andExpect(jsonPath("$.keys[?(@.kid=='" + next.getKeyID() + "')].kty").value("EC"))
				.andExpect(jsonPath("$.keys[?(@.kid=='" + next.getKeyID() + "')].x").isNotEmpty())
				.andExpect(jsonPath("$.keys[?(@.kid=='" + next.getKeyID() + "')].y").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("\"d\"");
		mockMvc.perform(get("/api/v1/checkin/keys").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(customer).bearer())).andExpect(status().isForbidden());
	}

	// ---- helpers ----

	private record Ticket(UUID id, String token) {
	}

	private String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer();
	}

	private ResultActions checkIn(String authorization, String token, String gate) throws Exception {
		return mockMvc.perform(post("/api/v1/checkins").header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"qrToken\":\"%s\",\"gate\":\"%s\"}".formatted(token, gate)));
	}

	/** Vé của sự kiện với QR ký bằng khoá hiện tại của app, ghế A-B-7. */
	private Ticket ticket(String status) {
		UUID id = UUID.randomUUID();
		String token = signer.sign(id, eventId, "A-B-7");
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'Sự kiện check-in', ?, 'Sài Gòn Arena', 'A', 'B', 7, 'VIP', ?)
				""", id, UUID.randomUUID(), eventId, UUID.randomUUID(), customer, status, token, signer.keyId(),
				Timestamp.from(NOW), Timestamp.from(NOW));
		return new Ticket(id, token);
	}

	private UUID insertTicket(String status, String token, String keyId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'Sự kiện check-in', ?, 'Sài Gòn Arena', 'A', 'B', 8, 'VIP', ?)
				""", id, UUID.randomUUID(), eventId, UUID.randomUUID(), customer, status, token, keyId,
				Timestamp.from(NOW), Timestamp.from(NOW));
		return id;
	}

	/** JWT như Ticket phát, ký bằng khoá được chỉ định (để dựng vé của khoá khác, vé giả). */
	private static String sign(ECKey key, UUID ticketId, UUID eventId) throws JOSEException {
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
				new JWTClaimsSet.Builder().claim("ticketId", ticketId.toString()).claim("eventId", eventId.toString())
						.claim("seat", "A-B-7").claim("kid", key.getKeyID()).build());
		jwt.sign(new ECDSASigner(key));
		return jwt.serialize();
	}

	private String statusOf(UUID ticketId) {
		return jdbc.queryForObject("select status from ticket.tickets where id = ?", String.class, ticketId);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

}
