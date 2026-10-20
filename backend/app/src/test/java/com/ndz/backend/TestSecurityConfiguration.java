package com.ndz.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Trỏ `app` tới JWKS của test và mở riêng `/test-probe/**` (endpoint chỉ có trong test) khỏi yêu cầu đăng nhập. */
@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {

	@Bean
	DynamicPropertyRegistrar securityProperties() {
		return registry -> {
			registry.add("tickethub.security.jwk-set-uri", TestTokens::jwkSetUri);
			registry.add("tickethub.security.issuer-uri", () -> TestTokens.ISSUER);
			// Endpoint chỉ có trong test không được lọt vào hợp đồng OpenAPI gửi cho frontend.
			registry.add("springdoc.paths-to-exclude", () -> "/test-probe/**,/api/v1/test-probe/**");
		};
	}

	@Bean
	@Order(1)
	SecurityFilterChain probeFilterChain(HttpSecurity http) {
		http.securityMatcher("/test-probe/**")
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

}
