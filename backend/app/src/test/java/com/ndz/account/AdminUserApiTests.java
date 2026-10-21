package com.ndz.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.IntegrationTest;
import com.ndz.KeycloakTestApi;
import com.ndz.TestTokens;

/**
 * Quản lý người dùng của Admin (overview.md §6.6) với Keycloak thật: tìm / lọc, chi tiết, khoá và mở khoá (refresh
 * token mất hiệu lực, đăng nhập bị từ chối), không khoá chính mình, chỉ ADMIN. Map: AC-9 của Sprint 5.
 */
@IntegrationTest
class AdminUserApiTests {

	private static final String USERS = "/api/v1/admin/users";
	private static final String DOMAIN = "@admin-users.test";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	KeycloakTestApi keycloak;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account.user_profiles where email like ?", "%" + DOMAIN);
	}

	// ---- Danh sách ----

	@Test
	void onlyAdminCanUseTheUserApi() throws Exception {
		UUID user = profile("alice", "Alice Nguyen", "CUSTOMER");

		mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, token(user, "CUSTOMER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(USERS + "/" + user + "/lock").header(HttpHeaders.AUTHORIZATION,
				token(user, "CUSTOMER", "ORGANIZER"))).andExpect(status().isForbidden());
	}

	@Test
	void searchMatchesNameEmailAndUsernameCaseInsensitivelyAndFiltersByRole() throws Exception {
		UUID alice = profile("alice", "Alice Nguyen", "CUSTOMER");
		UUID bob = profile("bob", "Bob Tran", "CUSTOMER", "ORGANIZER");
		profile("carol", "Carol Le", "CUSTOMER");

		mockMvc.perform(get(USERS + "?q=ALICE").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].userId").value(alice.toString()))
				.andExpect(jsonPath("$.items[0].email").value("alice" + DOMAIN));
		mockMvc.perform(get(USERS + "?q=tran").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(jsonPath("$.items[0].userId").value(bob.toString()));
		mockMvc.perform(get(USERS + "?q=" + DOMAIN.substring(1) + "&role=ORGANIZER")
				.header(HttpHeaders.AUTHORIZATION, admin())).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].userId").value(bob.toString()));
		mockMvc.perform(get(USERS + "?q=" + DOMAIN.substring(1) + "&size=2&page=1")
				.header(HttpHeaders.AUTHORIZATION, admin())).andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void searchTextIsLiteralNotALikePattern() throws Exception {
		profile("dave", "Dave Pham", "CUSTOMER");

		mockMvc.perform(get(USERS + "?q=%25").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	// ---- Chi tiết ----

	@Test
	void detailShowsProfileAndEmptyListsForANewUser() throws Exception {
		UUID user = profile("erin", "Erin Vo", "CUSTOMER");

		mockMvc.perform(get(USERS + "/" + user).header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.profile.userId").value(user.toString()))
				.andExpect(jsonPath("$.profile.enabled").value(true))
				.andExpect(jsonPath("$.bookings.length()").value(0))
				.andExpect(jsonPath("$.tickets.length()").value(0))
				.andExpect(jsonPath("$.refunds.length()").value(0))
				.andExpect(jsonPath("$.events").doesNotExist());
	}

	@Test
	void detailOfAnOrganizerIncludesTheirEventsAndUnknownUserIs404() throws Exception {
		UUID organizer = profile("frank", "Frank Do", "CUSTOMER", "ORGANIZER");

		mockMvc.perform(get(USERS + "/" + organizer).header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0));
		mockMvc.perform(get(USERS + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	// ---- Khoá / mở khoá ----

	@Test
	void lockingDisablesLoginKillsRefreshTokensAndUnlockRestoresThem() throws Exception {
		String username = "lockme-" + Instant.now().toEpochMilli();
		UUID user = keycloak.createUser(username, username + DOMAIN);
		touchProfile(user, username);
		String refreshToken = keycloak.login(username, KeycloakTestApi.PASSWORD);
		assertThat(refreshToken).isNotNull();
		assertThat(keycloak.refreshStatus(refreshToken)).isEqualTo(200);

		mockMvc.perform(post(USERS + "/" + user + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

		assertThat(keycloak.isEnabled(user)).isFalse();
		assertThat(keycloak.sessionCount(user)).isZero();
		assertThat(keycloak.refreshStatus(refreshToken)).isEqualTo(400);
		assertThat(keycloak.login(username, KeycloakTestApi.PASSWORD)).isNull();
		assertThat(enabledInProfile(user)).isFalse();

		// Khoá lại lần nữa không lỗi.
		mockMvc.perform(post(USERS + "/" + user + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk());

		mockMvc.perform(post(USERS + "/" + user + "/unlock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
		assertThat(keycloak.isEnabled(user)).isTrue();
		assertThat(keycloak.login(username, KeycloakTestApi.PASSWORD)).isNotNull();
		assertThat(enabledInProfile(user)).isTrue();
	}

	@Test
	void lockAndUnlockEachWriteOneAuditRowAndRepeatedLockWritesNone() throws Exception {
		String username = "audit-" + Instant.now().toEpochMilli();
		UUID user = keycloak.createUser(username, username + DOMAIN);
		touchProfile(user, username);

		mockMvc.perform(post(USERS + "/" + user + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk());
		mockMvc.perform(post(USERS + "/" + user + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk());
		mockMvc.perform(post(USERS + "/" + user + "/unlock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForList("select action from shared.audit_log where target_id = ? order by created_at",
				String.class, user.toString())).containsExactly("USER_LOCKED", "USER_UNLOCKED");
		assertThat(jdbc.queryForObject("""
				select count(*) from shared.audit_log
				where target_id = ? and actor_id = ? and before_state ->> 'enabled' = 'true'
				  and after_state ->> 'enabled' = 'false'
				""", Integer.class, user.toString(), TestTokens.ADMIN_ID)).isEqualTo(1);
		// Envers ghi lịch sử enabled của hồ sơ: bị khoá rồi mở lại.
		assertThat(jdbc.queryForList("select enabled from account.user_profiles_aud where user_id = ? order by rev",
				Boolean.class, user)).containsExactly(false, true);
	}

	@Test
	void lockingYourselfOrAnUnknownUserWritesNoAuditRow() throws Exception {
		mockMvc.perform(post(USERS + "/" + TestTokens.ADMIN_ID + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isUnprocessableContent());
		UUID unknown = UUID.randomUUID();
		mockMvc.perform(post(USERS + "/" + unknown + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("select count(*) from shared.audit_log where target_id in (?, ?)",
				Integer.class, TestTokens.ADMIN_ID.toString(), unknown.toString())).isZero();
	}

	@Test
	void adminCannotLockThemselves() throws Exception {
		mockMvc.perform(post(USERS + "/" + TestTokens.ADMIN_ID + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CANNOT_LOCK_SELF"));
		assertThat(keycloak.isEnabled(TestTokens.ADMIN_ID)).isTrue();
	}

	@Test
	void lockingAnUnknownUserIs404AndTouchesNothing() throws Exception {
		mockMvc.perform(post(USERS + "/" + UUID.randomUUID() + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void lockingAProfileThatKeycloakDoesNotKnowIsReportedAndLeavesTheProfileEnabled() throws Exception {
		UUID orphan = profile("ghost", "Ghost User", "CUSTOMER");

		mockMvc.perform(post(USERS + "/" + orphan + "/lock").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isNotFound());
		assertThat(enabledInProfile(orphan)).isTrue();
	}

	// ---- Hỗ trợ ----

	private UUID profile(String username, String fullName, String... roles) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into account.user_profiles (user_id, email, full_name, username, roles, created_at, updated_at)
				values (?, ?, ?, ?, string_to_array(?, ','), ?, ?)
				""", id, username + DOMAIN, fullName, username, String.join(",", roles), Timestamp.from(Instant.now()),
				Timestamp.from(Instant.now()));
		return id;
	}

	/** Hồ sơ được tạo ở request đầu tiên của người dùng (đồng bộ từ token), giống khi họ dùng app. */
	private void touchProfile(UUID user, String username) throws Exception {
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(user).email(username + DOMAIN).username(username).roles("CUSTOMER")
						.bearer()))
				.andExpect(status().isOk());
	}

	private boolean enabledInProfile(UUID user) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select enabled from account.user_profiles where user_id = ?",
				Boolean.class, user));
	}

	private static String admin() {
		return TestTokens.token().subject(TestTokens.ADMIN_ID).roles("CUSTOMER", "ADMIN").bearer();
	}

	private static String token(UUID user, String... roles) {
		return TestTokens.token().subject(user).roles(roles).bearer();
	}

}
