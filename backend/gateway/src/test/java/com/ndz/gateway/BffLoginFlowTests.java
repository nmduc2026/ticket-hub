package com.ndz.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Value;

/**
 * Luồng BFF end-to-end qua HTTP thật: Keycloak được thay bằng WireMock (discovery, token, JWKS, end_session), Redis
 * thật bằng Testcontainers. Map: overview.md §13.1, ADR 0003; AC-1, AC-2, AC-6 của Sprint 1.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BffLoginFlowTests {

	private static final String REALM = "/realms/tickethub";
	private static final String ACCESS_TOKEN = "access-token-for-test";
	private static final RSAKey SIGNING_KEY = newSigningKey();
	private static final WireMockServer IDP_AND_APP = startWireMock();

	@Container
	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.5").withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> idpBase() + REALM);
		registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> "test-secret");
		registry.add("tickethub.gateway.app-uri", BffLoginFlowTests::idpBase);
	}

	@Value("${local.server.port}")
	private int port;

	private CookieManager cookies;
	private HttpClient browser;

	@BeforeEach
	void newBrowser() {
		IDP_AND_APP.resetRequests();
		cookies = new CookieManager();
		browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
		IDP_AND_APP.stubFor(get(urlPathEqualTo("/api/v1/me")).willReturn(aResponse().withStatus(200).withBody("{}")));
		IDP_AND_APP.stubFor(post(urlPathEqualTo("/api/v1/bookings")).willReturn(aResponse().withStatus(201)));
		IDP_AND_APP.stubFor(post(urlPathEqualTo("/api/v1/webhooks/stripe")).willReturn(aResponse().withStatus(200)));
		IDP_AND_APP.stubFor(get(urlPathEqualTo("/api/v1/events")).willReturn(aResponse().withStatus(200).withBody("{}")));
		IDP_AND_APP.stubFor(
				get(urlPathEqualTo("/api/v1/events/42/seats")).willReturn(aResponse().withStatus(200).withBody("{}")));
	}

	@Test
	void unauthenticatedXhrGets401WhileBrowserNavigationIsSentToLogin() throws Exception {
		HttpResponse<String> xhr = send(navigate("/api/v1/me").header("X-Requested-With", "XMLHttpRequest"));
		HttpResponse<String> navigation = send(navigate("/api/v1/me"));

		assertThat(xhr.statusCode()).isEqualTo(401);
		assertThat(navigation.statusCode()).isEqualTo(302);
		assertThat(navigation.headers().firstValue("Location").orElseThrow())
				.endsWith("/oauth2/authorization/keycloak");
		assertThat(IDP_AND_APP.findAll(getRequestedFor(urlPathEqualTo("/api/v1/me")))).isEmpty();
	}

	@Test
	void loginUsesPkceReturnsToSameAndRelaysBearerWithoutSessionCookie() throws Exception {
		login("/events/42");

		assertThat(cookieNames()).containsExactlyInAnyOrder("SESSION", "XSRF-TOKEN");
		HttpResponse<String> me = send(navigate("/api/v1/me").header("X-Requested-With", "XMLHttpRequest"));
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).doesNotContain(ACCESS_TOKEN);
		assertThat(me.headers().map().toString()).doesNotContain(ACCESS_TOKEN);

		List<com.github.tomakehurst.wiremock.verification.LoggedRequest> relayed = IDP_AND_APP
				.findAll(getRequestedFor(urlPathEqualTo("/api/v1/me")));
		assertThat(relayed).hasSize(1);
		assertThat(relayed.get(0).getHeader("Authorization")).isEqualTo("Bearer " + ACCESS_TOKEN);
		assertThat(relayed.get(0).containsHeader("Cookie")).isFalse();
		IDP_AND_APP.verify(postRequestedFor(urlPathEqualTo(REALM + "/protocol/openid-connect/token"))
				.withRequestBody(containing("code_verifier=")));
	}

	@Test
	void returnToIsOnlyHonouredForLocalPaths() throws Exception {
		String location = login("//evil.example/steal");

		assertThat(location).isEqualTo("http://localhost:" + port + "/");
	}

	@Test
	void publicEventApiIsReachableWithoutLoginAndWithoutBearerToken() throws Exception {
		HttpResponse<String> list = sendWithoutCookies(navigate("/api/v1/events?q=rock"));
		HttpResponse<String> seats = sendWithoutCookies(navigate("/api/v1/events/42/seats"));
		HttpResponse<String> privateApi = sendWithoutCookies(
				navigate("/api/v1/organizer/events").header("X-Requested-With", "XMLHttpRequest"));

		assertThat(list.statusCode()).isEqualTo(200);
		assertThat(seats.statusCode()).isEqualTo(200);
		assertThat(privateApi.statusCode()).isEqualTo(401);
		assertThat(IDP_AND_APP.findAll(getRequestedFor(urlPathEqualTo("/api/v1/events"))).get(0)
				.containsHeader("Authorization")).isFalse();
	}

	@Test
	void writesNeedCsrfTokenAndWebhooksAreExempt() throws Exception {
		login("/");

		HttpResponse<String> withoutToken = send(submit("/api/v1/bookings").header("X-Requested-With", "XMLHttpRequest"));
		HttpResponse<String> withToken = send(submit("/api/v1/bookings").header("X-XSRF-TOKEN", xsrfToken()));
		HttpResponse<String> webhook = sendWithoutCookies(submit("/api/v1/webhooks/stripe"));

		assertThat(withoutToken.statusCode()).isEqualTo(403);
		assertThat(withToken.statusCode()).isEqualTo(201);
		assertThat(webhook.statusCode()).isEqualTo(200);
		assertThat(IDP_AND_APP.findAll(postRequestedFor(urlPathEqualTo("/api/v1/webhooks/stripe"))).get(0)
				.containsHeader("Authorization")).isFalse();
	}

	@Test
	void logoutDestroysGatewaySessionAndRedirectsToKeycloakEndSession() throws Exception {
		login("/");

		HttpResponse<String> logout = send(submit("/logout").header("X-XSRF-TOKEN", xsrfToken()));
		HttpResponse<String> afterLogout = send(navigate("/api/v1/me").header("X-Requested-With", "XMLHttpRequest"));

		assertThat(logout.statusCode()).isEqualTo(302);
		String endSession = logout.headers().firstValue("Location").orElseThrow();
		assertThat(endSession).startsWith(idpBase() + REALM + "/protocol/openid-connect/logout");
		assertThat(endSession).contains("id_token_hint=").contains("post_logout_redirect_uri=");
		assertThat(afterLogout.statusCode()).isEqualTo(401);
	}

	@Test
	void xhrLogoutReturnsEndSessionUrlInsteadOfARedirect() throws Exception {
		login("/");

		HttpResponse<String> logout = send(submit("/logout").header("X-XSRF-TOKEN", xsrfToken())
				.header("X-Requested-With", "XMLHttpRequest"));
		HttpResponse<String> afterLogout = send(navigate("/api/v1/me").header("X-Requested-With", "XMLHttpRequest"));

		assertThat(logout.statusCode()).isEqualTo(200);
		assertThat(logout.body()).startsWith("{\"logoutUrl\":\"" + idpBase() + REALM + "/protocol/openid-connect/logout");
		assertThat(logout.body()).contains("id_token_hint=");
		assertThat(afterLogout.statusCode()).isEqualTo(401);
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		HttpResponse<String> response = send(navigate("/actuator/health"));

		assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
		assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
		assertThat(response.headers().firstValue("Content-Security-Policy")).hasValue("frame-ancestors 'none'");
	}

	/** Đăng nhập như trình duyệt; trả về Location sau khi Gateway xử lý callback. */
	private String login(String returnTo) throws Exception {
		String encoded = java.net.URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
		HttpResponse<String> start = send(navigate("/oauth2/authorization/keycloak?returnTo=" + encoded));
		assertThat(start.statusCode()).isEqualTo(302);
		Map<String, String> authorize = queryOf(start.headers().firstValue("Location").orElseThrow());
		assertThat(authorize).containsEntry("code_challenge_method", "S256").containsKey("code_challenge");

		IDP_AND_APP.stubFor(post(urlPathEqualTo(REALM + "/protocol/openid-connect/token"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
						{"access_token":"%s","refresh_token":"refresh-token","token_type":"Bearer","expires_in":300,
						"id_token":"%s"}""".formatted(ACCESS_TOKEN, idToken(authorize.get("nonce"))))));

		HttpResponse<String> callback = send(
				navigate("/login/oauth2/code/keycloak?code=auth-code&state=" + authorize.get("state")));
		assertThat(callback.statusCode()).isEqualTo(302);
		// Đăng nhập xoá cookie XSRF-TOKEN cũ (đổi session); request kế tiếp của SPA nhận cookie mới.
		send(navigate("/actuator/health"));
		return callback.headers().firstValue("Location").orElseThrow();
	}

	private String xsrfToken() {
		return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
				.findFirst().orElseThrow().getValue();
	}

	private List<String> cookieNames() {
		return cookies.getCookieStore().getCookies().stream().map(HttpCookie::getName).toList();
	}

	private HttpRequest.Builder navigate(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
	}

	private HttpRequest.Builder submit(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).POST(BodyPublishers.noBody());
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return browser.send(request.build(), BodyHandlers.ofString());
	}

	private HttpResponse<String> sendWithoutCookies(HttpRequest.Builder request) throws Exception {
		HttpClient anonymous = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		return anonymous.send(request.build(), BodyHandlers.ofString());
	}

	private static Map<String, String> queryOf(String location) {
		return java.util.Arrays.stream(URI.create(location).getRawQuery().split("&")).map(p -> p.split("=", 2))
				.collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
	}

	private static String idToken(String nonce) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(idpBase() + REALM).subject(UUID.randomUUID().toString())
				.audience("tickethub-bff").claim("nonce", nonce).claim("preferred_username", "customer")
				.issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(SIGNING_KEY));
		return jwt.serialize();
	}

	private static String idpBase() {
		return "http://localhost:" + IDP_AND_APP.port();
	}

	private static RSAKey newSigningKey() {
		try {
			return new RSAKeyGenerator(2048).keyID("test-key").generate();
		}
		catch (com.nimbusds.jose.JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Discovery và JWKS phải có trước khi context khởi động (Spring đọc discovery lúc tạo ClientRegistration). */
	private static WireMockServer startWireMock() {
		WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		String issuer = "http://localhost:" + server.port() + REALM;
		String oidc = issuer + "/protocol/openid-connect";
		server.stubFor(get(urlEqualTo(REALM + "/.well-known/openid-configuration"))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
						{"issuer":"%1$s","authorization_endpoint":"%2$s/auth","token_endpoint":"%2$s/token",
						"jwks_uri":"%2$s/certs","end_session_endpoint":"%2$s/logout",
						"subject_types_supported":["public"],"response_types_supported":["code"],
						"id_token_signing_alg_values_supported":["RS256"],
						"token_endpoint_auth_methods_supported":["client_secret_basic"]}""".formatted(issuer, oidc))));
		server.stubFor(get(urlEqualTo(REALM + "/protocol/openid-connect/certs")).willReturn(aResponse()
				.withHeader("Content-Type", "application/json").withBody(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
		return server;
	}

}
