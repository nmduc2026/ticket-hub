package com.ndz.gateway;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/** Quay về trang SPA đã gửi qua {@code returnTo}; không có thì dùng request đã lưu, cuối cùng là {@code /}. */
final class ReturnToAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final SavedRequestAwareAuthenticationSuccessHandler fallback = new SavedRequestAwareAuthenticationSuccessHandler();

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		HttpSession session = request.getSession(false);
		Object returnTo = session == null ? null : session.getAttribute(ReturnToFilter.SESSION_ATTRIBUTE);
		if (returnTo instanceof String path && ReturnToFilter.isLocalPath(path)) {
			session.removeAttribute(ReturnToFilter.SESSION_ATTRIBUTE);
			response.sendRedirect(request.getContextPath() + path);
			return;
		}
		fallback.onAuthenticationSuccess(request, response, authentication);
	}

}
