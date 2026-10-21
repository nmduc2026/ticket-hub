package com.ndz.backend.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.TestTokens;

/**
 * `app` là Resource Server (overview.md §13.1, §13.3): chữ ký, {@code iss}, {@code aud}, hạn, role. Map: AC-4, AC-5
 * của Sprint 1.
 */
@IntegrationTest
class ResourceServerSecurityTests {

	private static final String ADMIN_ONLY = "/api/v1/test-probe/admin";

	@Autowired
	MockMvc mockMvc;

	@Test
	void requestWithoutTokenIsRejectedWith401ProblemDetails() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void tokenForAnotherAudienceIsRejected() throws Exception {
		String token = TestTokens.token().audience("account").bearer();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithoutAnyAudienceIsRejected() throws Exception {
		String token = TestTokens.token().audience().bearer();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() throws Exception {
		String token = TestTokens.token().issuer("http://evil.example/realms/tickethub").bearer();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		String token = TestTokens.token().expiresAt(Instant.now().minusSeconds(120)).bearer();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithForgedSignatureIsRejected() throws Exception {
		String token = TestTokens.token().signedByUnknownKey().bearer();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void customerCannotCallAdminEndpoint() throws Exception {
		mockMvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, TestTokens.token().roles("CUSTOMER").bearer()))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void adminCanCallAdminEndpoint() throws Exception {
		String token = TestTokens.token().subject(TestTokens.ADMIN_ID).roles("CUSTOMER", "ADMIN").bearer();

		mockMvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk());
	}

	@Test
	void onlyApplicationRolesBecomeAuthorities() throws Exception {
		// Keycloak còn cấp offline_access, uma_authorization, default-roles-*; chúng không được thành ROLE_*.
		String token = TestTokens.token().roles("CUSTOMER", "offline_access", "ADMIN_LOOKALIKE").bearer();

		mockMvc.perform(get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isForbidden());
	}

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
