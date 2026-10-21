package com.ndz.backend.shared.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Token phải dành cho API này; Keycloak mặc định chỉ cấp {@code aud: account} nên cần audience mapper. */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String audience;

	AudienceValidator(String audience) {
		this.audience = audience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		if (jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(
				new OAuth2Error("invalid_token", "Token không dành cho " + audience, null));
	}

}
