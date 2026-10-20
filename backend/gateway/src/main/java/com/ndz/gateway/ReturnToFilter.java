package com.ndz.gateway;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SPA chuyển người dùng tới {@code /oauth2/authorization/keycloak?returnTo=/trang-dang-xem}; filter nhớ đường dẫn đó
 * trong session để đăng nhập xong quay lại đúng trang (overview.md §13.1, "Sau đăng nhập").
 */
final class ReturnToFilter extends OncePerRequestFilter {

	static final String SESSION_ATTRIBUTE = ReturnToFilter.class.getName() + ".RETURN_TO";
	static final String PARAMETER = "returnTo";
	private static final String LOGIN_PATH_PREFIX = "/oauth2/authorization/";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(LOGIN_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String returnTo = request.getParameter(PARAMETER);
		if (isLocalPath(returnTo)) {
			request.getSession().setAttribute(SESSION_ATTRIBUTE, returnTo);
		}
		chain.doFilter(request, response);
	}

	/** Chỉ nhận đường dẫn nội bộ: "//host" và "/\host" là open redirect. */
	static boolean isLocalPath(String path) {
		return path != null && path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\")
				&& path.chars().noneMatch(Character::isISOControl);
	}

}
