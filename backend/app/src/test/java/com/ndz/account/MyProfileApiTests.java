package com.ndz.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.KeycloakTestApi;
import com.ndz.TestTokens;

/**
 * Hồ sơ cá nhân #19 (plan sprint dọn dẹp việc 6, AC-5; overview.md §6.6) với Keycloak thật: sửa SĐT / ngày sinh / tỉnh,
 * ảnh đại diện, xem và đăng xuất từng phiên (không đăng xuất phiên hiện tại), xoá tài khoản (chặn khi còn vé sắp diễn).
 */
@IntegrationTest
class MyProfileApiTests {

	private static final String DOMAIN = "@my-profile.test";
	private static final byte[] PNG = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAwS2OUAAAAABJRU5ErkJggg==");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	KeycloakTestApi keycloak;

	private final UUID customer = UUID.randomUUID();
	private final String email = "khach-" + customer + DOMAIN;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from ticket.tickets where owner_user_id = ?", customer);
		jdbc.update("delete from account.user_profiles_aud where user_id = ?", customer);
		jdbc.update("delete from account.user_profiles where email like ? or user_id = ?", "%" + DOMAIN, customer);
	}

	// ---- Hồ sơ ----

	@Test
	@DisplayName("AC-5: sửa SĐT (bỏ dấu cách), ngày sinh, tỉnh rồi đọc lại; email / tên lấy từ Keycloak")
	void customerEditsPhoneBirthDateAndProvince() throws Exception {
		UUID province = jdbc.queryForObject("select id from catalog.administrative_units where level = 1 limit 1",
				UUID.class);

		updateProfile(bearer(), "{\"phone\":\"0912 345 678\",\"dateOfBirth\":\"1995-04-30\",\"provinceId\":\"%s\"}"
				.formatted(province)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.phone").value("0912345678"))
				.andExpect(jsonPath("$.dateOfBirth").value("1995-04-30"))
				.andExpect(jsonPath("$.province.id").value(province.toString()))
				.andExpect(jsonPath("$.province.name").isNotEmpty());
	}

	@Test
	@DisplayName("SĐT sai → PHONE_INVALID; ngày sinh tương lai → DATE_OF_BIRTH_INVALID; phường (cấp 2) → PROVINCE_NOT_FOUND")
	void invalidProfileDataIsRejected() throws Exception {
		UUID ward = jdbc.queryForObject("select id from catalog.administrative_units where level = 2 limit 1",
				UUID.class);

		updateProfile(bearer(), "{\"phone\":\"12345\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PHONE_INVALID"));
		updateProfile(bearer(), "{\"dateOfBirth\":\"2999-01-01\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DATE_OF_BIRTH_INVALID"));
		updateProfile(bearer(), "{\"provinceId\":\"%s\"}".formatted(ward)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PROVINCE_NOT_FOUND"));
	}

	@Test
	@DisplayName("AC-5: tải ảnh đại diện png → có avatarUrl trong hồ sơ; file không phải ảnh → 422 UNSUPPORTED_IMAGE_TYPE")
	void avatarUpload() throws Exception {
		String url = com.jayway.jsonpath.JsonPath.read(uploadAvatar(PNG).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(), "$.avatarUrl");

		assertThat(url).contains("avatars/" + customer + "/");
		mockMvc.perform(get("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(jsonPath("$.avatarUrl").value(url));
		uploadAvatar("không phải ảnh".getBytes()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
	}

	// ---- Phiên đăng nhập ----

	@Test
	@DisplayName("AC-5: xem 2 phiên (đánh dấu phiên hiện tại), đăng xuất phiên kia; phiên hiện tại → 409; phiên của người khác → 404")
	void listAndEndSessions() throws Exception {
		String username = "phien-" + UUID.randomUUID().toString().substring(0, 8);
		UUID user = keycloak.createUser(username, username + DOMAIN);
		String current = keycloak.loginSessionId(username);
		String other = keycloak.loginSessionId(username);
		String stranger = "la-" + UUID.randomUUID().toString().substring(0, 8);
		keycloak.createUser(stranger, stranger + DOMAIN);
		String strangersSession = keycloak.loginSessionId(stranger);
		String bearer = TestTokens.token().subject(user).email(username + DOMAIN).sessionId(current).bearer();

		mockMvc.perform(get("/api/v1/me/sessions").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.id=='" + current + "')].current").value(true))
				.andExpect(jsonPath("$[?(@.id=='" + other + "')].current").value(false));

		endSession(bearer, current).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_END_CURRENT_SESSION"));
		endSession(bearer, strangersSession).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
		endSession(bearer, other).andExpect(status().isNoContent());

		assertThat(keycloak.sessionCount(user)).isEqualTo(1);
	}

	// ---- Xoá tài khoản ----

	@Test
	@DisplayName("AC-5: còn vé của sự kiện chưa diễn → 409 ACCOUNT_HAS_UPCOMING_TICKETS; email gõ lại sai → 422; tài khoản còn nguyên")
	void accountDeletionIsBlockedByUpcomingTicketsOrAWrongEmail() throws Exception {
		String username = "xoa-" + UUID.randomUUID().toString().substring(0, 8);
		UUID user = keycloak.createUser(username, username + DOMAIN);
		String bearer = TestTokens.token().subject(user).email(username + DOMAIN).bearer();
		insertTicket(user, "VALID", Instant.now().plusSeconds(86_400));
		try {
			deleteAccount(bearer, "khac" + DOMAIN).andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("CONFIRM_EMAIL_MISMATCH"));
			deleteAccount(bearer, username + DOMAIN).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("ACCOUNT_HAS_UPCOMING_TICKETS"));
			assertThat(keycloak.exists(user)).isTrue();
		}
		finally {
			jdbc.update("delete from ticket.tickets where owner_user_id = ?", user);
		}
	}

	@Test
	@DisplayName("AC-5: xoá tài khoản → user mất ở Keycloak, hồ sơ ẩn danh hoá, vé đã qua giữ lại; request sau với token cũ không ghi lại email")
	void deletingTheAccountRemovesTheKeycloakUserAndAnonymizesTheProfile() throws Exception {
		String username = "xoa-" + UUID.randomUUID().toString().substring(0, 8);
		UUID user = keycloak.createUser(username, username + DOMAIN);
		String bearer = TestTokens.token().subject(user).email(username + DOMAIN).bearer();
		UUID pastTicket = insertTicket(user, "USED", Instant.now().minusSeconds(86_400));
		try {
			updateProfile(bearer, "{\"phone\":\"0912345678\"}").andExpect(status().isOk());

			deleteAccount(bearer, username.toUpperCase() + DOMAIN).andExpect(status().isNoContent());

			assertThat(keycloak.exists(user)).isFalse();
			Map<String, Object> profile = jdbc.queryForMap("select * from account.user_profiles where user_id = ?",
					user);
			assertThat(profile).containsEntry("email", null).containsEntry("phone", null)
					.containsEntry("enabled", false);
			assertThat(profile.get("deleted_at")).isNotNull();
			assertThat(jdbc.queryForObject("select count(*) from ticket.tickets where id = ?", Integer.class,
					pastTicket)).isEqualTo(1);

			// Access token cũ còn hạn: đồng bộ hồ sơ không ghi lại email, API hồ sơ coi như không còn.
			mockMvc.perform(get("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, bearer))
					.andExpect(status().isNotFound());
			assertThat(jdbc.queryForObject("select email from account.user_profiles where user_id = ?", String.class,
					user)).isNull();
		}
		finally {
			jdbc.update("delete from ticket.tickets where owner_user_id = ?", user);
			jdbc.update("delete from account.user_profiles_aud where user_id = ?", user);
		}
	}

	// ---- helpers ----

	private String bearer() {
		return TestTokens.token().subject(customer).email(email).bearer();
	}

	private ResultActions updateProfile(String bearer, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions uploadAvatar(byte[] content) throws Exception {
		return mockMvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("file", "a.png", "image/png",
				content)).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.MULTIPART_FORM_DATA));
	}

	private ResultActions endSession(String bearer, String sessionId) throws Exception {
		return mockMvc.perform(delete("/api/v1/me/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private ResultActions deleteAccount(String bearer, String confirmEmail) throws Exception {
		return mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"confirmEmail\":\"%s\"}".formatted(confirmEmail)));
	}

	private UUID insertTicket(UUID owner, String status, Instant startsAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into ticket.tickets (id, booking_id, event_id, seat_id, owner_user_id, status, qr_token, key_id,
				    event_title, starts_at, venue_name, section_code, row_label, seat_number, tier_name, issued_at)
				values (?, ?, ?, ?, ?, ?, 'x', 'k', 'Sự kiện', ?, 'Nơi', 'A', 'B', 1, 'VIP', ?)
				""", id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), owner, status, Timestamp.from(startsAt),
				Timestamp.from(Instant.now()));
		return id;
	}

}
