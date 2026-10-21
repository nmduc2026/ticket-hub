package com.ndz.shared.config;

import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.ProblemWriter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.ndz.shared.idempotency.IdempotencyFilter;

/**
 * `app` là OAuth2 Resource Server (overview.md §13.1, §13.3): stateless, không CSRF (Gateway kiểm), kiểm chữ ký,
 * hạn, {@code iss}, {@code aud=tickethub-api}; role từ {@code realm_access.roles}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(ResourceServerProperties.class)
class ResourceServerConfiguration {

	@Bean
	SecurityFilterChain resourceServerFilterChain(HttpSecurity http, IdempotencyFilter idempotencyFilter) {
		http
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						// Hợp đồng API (OpenAPI) là nguồn sinh client cho frontend; không chứa dữ liệu.
						.requestMatchers("/v3/api-docs/**").permitAll()
						// API công khai cho khách chưa đăng nhập: xem sự kiện, sơ đồ ghế, danh mục tỉnh / xã.
						.requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/events/*/seats",
								"/api/v1/administrative-units")
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRolesConverter()))
						.authenticationEntryPoint((request, response, ex) -> ProblemWriter.write(response, ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập hoặc token không hợp lệ")))
				.exceptionHandling(exceptions -> exceptions
						.accessDeniedHandler((request, response, ex) -> ProblemWriter.write(response, ErrorCode.FORBIDDEN, "Không đủ quyền thực hiện thao tác này")))
				// Sau xác thực và phân quyền theo đường dẫn, trước controller: tra Idempotency-Key trước mọi xử lý nghiệp vụ.
				.addFilterAfter(idempotencyFilter, AuthorizationFilter.class)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/** Dùng jwk-set-uri thay vì discovery để `app` khởi động được khi Keycloak chưa sẵn sàng. */
	@Bean
	JwtDecoder jwtDecoder(ResourceServerProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
				new AudienceValidator(properties.audience())));
		return decoder;
	}

}
