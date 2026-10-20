package com.ndz.gateway;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Cấu hình bảo mật theo bảng "Yêu cầu cấu hình bắt buộc" của overview.md §13.1 (ADR 0003). */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	private static final String LOGIN_URL = "/oauth2/authorization/keycloak";
	private static final String WEBHOOKS = "/api/v1/webhooks/**";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clients,
			OAuth2AuthorizedClientRepository authorizedClients) {
		http
				.authorizeHttpRequests(requests -> requests
						// Webhook xác thực bằng chữ ký Stripe, không cần session.
						.requestMatchers(WEBHOOKS).permitAll()
						// API công khai cho khách chưa đăng nhập; vẫn đi qua Gateway nhưng không có Bearer.
						.requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/events/*/seats",
								"/api/v1/administrative-units")
						.permitAll()
						.requestMatchers("/api/**").authenticated()
						// Phần còn lại (health, trang SPA): web server phía trước phục vụ.
						.anyRequest().permitAll())
				.oauth2Login(login -> login
						.authorizationEndpoint(endpoint -> endpoint
								.authorizationRequestResolver(pkceResolver(clients)))
						.authorizedClientRepository(authorizedClients)
						.successHandler(new ReturnToAuthenticationSuccessHandler()))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint()))
				.logout(logout -> logout.logoutSuccessHandler(new SpaLogoutSuccessHandler(clients)))
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.ignoringRequestMatchers(WEBHOOKS))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.addFilterBefore(new ReturnToFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
				.headers(headers -> headers
						.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
						.contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'")));
		return http.build();
	}

	/** Token (access + refresh) nằm trong session; session nằm ở Redis (Spring Session). */
	@Bean
	OAuth2AuthorizedClientRepository authorizedClientRepository() {
		return new HttpSessionOAuth2AuthorizedClientRepository();
	}

	/** Spring chỉ tự bật PKCE cho public client; client này confidential nên bật tường minh (S256). */
	private static OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clients) {
		DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(clients,
				"/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		return resolver;
	}

	/** XHR (header X-Requested-With) nhận 401 để SPA tự điều hướng; điều hướng trình duyệt được chuyển sang Keycloak. */
	private static DelegatingAuthenticationEntryPoint entryPoint() {
		LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
		entryPoints.put(new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"),
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
		DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
		entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint(LOGIN_URL));
		return entryPoint;
	}

}
