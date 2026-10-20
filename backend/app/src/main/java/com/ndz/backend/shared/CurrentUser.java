package com.ndz.backend.shared;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Người dùng của request hiện tại, dựng từ claim của access token. Đây là chỗ duy nhất đọc
 * {@code SecurityContextHolder}; code khác gọi {@link #require()} hoặc {@link #current()}.
 *
 * @param authTime thời điểm đăng nhập (claim {@code auth_time}), null nếu token không có
 */
public record CurrentUser(UUID id, String email, String fullName, String username, Set<String> roles,
		Instant authTime) {

	public static CurrentUser from(Jwt jwt) {
		return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"),
				jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"), KeycloakRolesConverter.roles(jwt),
				jwt.getClaimAsInstant("auth_time"));
	}

	public static Optional<CurrentUser> current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			return Optional.of(from(token.getToken()));
		}
		return Optional.empty();
	}

	public static CurrentUser require() {
		return current().orElseThrow(
				() -> new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED));
	}

}
