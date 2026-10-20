package com.ndz.backend.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * `app` là OAuth2 Resource Server (overview.md §13.1, §13.3): stateless, không CSRF (Gateway kiểm), kiểm chữ ký,
 * hạn, {@code iss}, {@code aud=tickethub-api}; role từ {@code realm_access.roles}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(ResourceServerProperties.class)
class ResourceServerConfiguration {

	@Bean
	SecurityFilterChain resourceServerFilterChain(HttpSecurity http) {
		http
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRolesConverter()))
						.authenticationEntryPoint((request, response, ex) -> writeProblem(response,
								HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Chưa đăng nhập hoặc token không hợp lệ")))
				.exceptionHandling(exceptions -> exceptions
						.accessDeniedHandler((request, response, ex) -> writeProblem(response, HttpStatus.FORBIDDEN,
								"FORBIDDEN", "Không đủ quyền thực hiện thao tác này")))
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

	/** Lỗi bảo mật xảy ra trước controller nên không qua {@code GlobalExceptionHandler}; trả cùng định dạng Problem Details. */
	private static void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
				.formatted(status.getReasonPhrase(), status.value(), detail, code));
	}

}
