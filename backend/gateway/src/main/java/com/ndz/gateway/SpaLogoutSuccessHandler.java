package com.ndz.gateway;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Đăng xuất qua Keycloak end_session. Điều hướng trình duyệt nhận redirect 302; request XHR của SPA ({@code fetch} không
 * đọc được {@code Location} của redirect) nhận 200 kèm {@code {"logoutUrl": ...}} để tự điều hướng.
 */
final class SpaLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

	SpaLogoutSuccessHandler(ClientRegistrationRepository clients) {
		super(clients);
		setPostLogoutRedirectUri("{baseUrl}/");
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
			super.onLogoutSuccess(request, response, authentication);
			return;
		}
		String logoutUrl = determineTargetUrl(request, response, authentication);
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"logoutUrl\":\"" + logoutUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
	}

}
