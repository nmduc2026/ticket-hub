package com.ndz.backend.shared;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Role lấy từ {@code realm_access.roles}; chỉ {@code CUSTOMER}, {@code ORGANIZER}, {@code ADMIN} thành {@code ROLE_*}
 * (bỏ {@code offline_access}, {@code uma_authorization}, {@code default-roles-*}).
 */
final class KeycloakRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	static final Set<String> APP_ROLES = Set.of("CUSTOMER", "ORGANIZER", "ADMIN");

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
	}

	static Set<String> roles(Jwt jwt) {
		if (jwt.getClaim("realm_access") instanceof Map<?, ?> realmAccess
				&& realmAccess.get("roles") instanceof Collection<?> roles) {
			return roles.stream().map(String::valueOf).filter(APP_ROLES::contains)
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
		return Set.of();
	}

	private static List<GrantedAuthority> authorities(Jwt jwt) {
		return roles(jwt).stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
	}

}
