package com.ndz.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.TestTokens;

/**
 * Đồng bộ hồ sơ khi đăng nhập và {@code GET /api/v1/me} (overview.md §6.6, ADR 0016). Map: AC-3 của Sprint 1.
 */
@IntegrationTest
class MeEndpointTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	/** Mỗi test một người dùng riêng: cache đồng bộ của service sống cùng context nên không dọn bảng giữa các test. */
	private UUID user;

	@BeforeEach
	void newUser() {
		user = UUID.randomUUID();
	}

	private TestTokens.Builder token() {
		return TestTokens.token().subject(user);
	}

	@Test
	void firstCallCreatesProfileAndReturnsItWithOnlyApplicationRoles() throws Exception {
		callMe(token().roles("CUSTOMER", "offline_access"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.toString()))
				.andExpect(jsonPath("$.email").value("customer@tickethub.local"))
				.andExpect(jsonPath("$.fullName").value("Khách Hàng"))
				.andExpect(jsonPath("$.username").value("customer"))
				.andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
				.andExpect(jsonPath("$.roles.length()").value(1))
				.andExpect(jsonPath("$.enabled").value(true));

		Map<String, Object> row = profileRow(user);
		assertThat(row).containsEntry("email", "customer@tickethub.local").containsEntry("username", "customer");
		assertThat(row.get("last_login_at")).isNotNull();
	}

	@Test
	void organizerAndAdminTokensKeepTheirOwnRoles() throws Exception {
		callMe(token().username("organizer").email("organizer@tickethub.local")
				.roles("CUSTOMER", "ORGANIZER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
				.andExpect(jsonPath("$.roles[1]").value("ORGANIZER"));
		callMe(token().username("admin").email("admin@tickethub.local")
				.roles("CUSTOMER", "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"));
	}

	@Test
	void changedNameInKeycloakIsCopiedOnTheNextToken() throws Exception {
		callMe(token()).andExpect(status().isOk());
		Instant createdAt = timestamp(user, "created_at");

		callMe(token().name("Tên Mới").email("moi@tickethub.local"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Tên Mới"))
				.andExpect(jsonPath("$.email").value("moi@tickethub.local"));

		assertThat(profileRow(user)).containsEntry("full_name", "Tên Mới");
		assertThat(timestamp(user, "created_at")).isEqualTo(createdAt);
	}

	@Test
	void promotedRoleIsCopiedButEnabledFlagIsNotTouched() throws Exception {
		callMe(token()).andExpect(status().isOk());
		jdbc.update("update account.user_profiles set enabled = false where user_id = ?", user);

		callMe(token().roles("CUSTOMER", "ORGANIZER"))
				.andExpect(jsonPath("$.roles[1]").value("ORGANIZER"))
				.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void unchangedClaimsDoNotWriteTheDatabaseAgain() throws Exception {
		Instant authTime = Instant.now().minusSeconds(60);
		callMe(token().authTime(authTime)).andExpect(status().isOk());
		jdbc.update("update account.user_profiles set updated_at = ? where user_id = ?",
				Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), user);

		// Claim y hệt lần trước: cache theo người dùng bỏ qua DB.
		callMe(token().authTime(authTime)).andExpect(status().isOk());

		assertThat(timestamp(user, "updated_at")).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
	}

	@Test
	void newLoginMovesLastLoginForwardButNeverBackwards() throws Exception {
		Instant first = Instant.parse("2026-10-20T01:00:00Z");
		Instant second = Instant.parse("2026-10-20T02:00:00Z");

		callMe(token().authTime(second)).andExpect(status().isOk());
		callMe(token().authTime(first)).andExpect(status().isOk());

		assertThat(timestamp(user, "last_login_at")).isEqualTo(second);
	}

	@Test
	void simultaneousFirstRequestsCreateExactlyOneProfile() throws Exception {
		String token = token().username("race").bearer();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> calls = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				calls.add(() -> mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token)).andReturn()
						.getResponse().getStatus());
			}
			for (Future<Integer> result : pool.invokeAll(calls)) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("select count(*) from account.user_profiles where user_id = ?", Integer.class,
				user)).isEqualTo(1);
	}

	private ResultActions callMe(TestTokens.Builder token) throws Exception {
		return mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token.bearer()));
	}

	private Map<String, Object> profileRow(UUID userId) {
		return jdbc.queryForMap("select * from account.user_profiles where user_id = ?", userId);
	}

	private Instant timestamp(UUID userId, String column) {
		return jdbc.queryForObject("select " + column + " from account.user_profiles where user_id = ?",
				Timestamp.class, userId).toInstant();
	}

}
