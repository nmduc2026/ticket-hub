package com.ndz.backend;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Phát access token giống Keycloak cho test: ký bằng khoá RSA của test, JWKS phục vụ qua WireMock. Mặc định token hợp
 * lệ với `app`; test đổi từng thuộc tính (aud, iss, hạn, chữ ký) để chứng minh token sai bị từ chối.
 */
public final class TestTokens {

	public static final String ISSUER = "http://keycloak.test/realms/tickethub";
	public static final String AUDIENCE = "tickethub-api";
	public static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
	public static final UUID ORGANIZER_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
	public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");

	private static final String JWKS_PATH = "/realms/tickethub/protocol/openid-connect/certs";
	private static final RSAKey SIGNING_KEY = newKey("test-key");
	private static final RSAKey OTHER_KEY = newKey("other-key");
	private static final WireMockServer JWKS_SERVER = startJwksServer();

	private TestTokens() {
	}

	public static String jwkSetUri() {
		return "http://localhost:" + JWKS_SERVER.port() + JWKS_PATH;
	}

	public static Builder token() {
		return new Builder();
	}

	public static final class Builder {

		private UUID subject = CUSTOMER_ID;
		private String email = "customer@tickethub.local";
		private String name = "Khách Hàng";
		private String username = "customer";
		private List<String> roles = List.of("CUSTOMER");
		private List<String> audience = List.of(AUDIENCE);
		private String issuer = ISSUER;
		private Instant authTime = Instant.now().minusSeconds(60);
		private Instant expiresAt = Instant.now().plusSeconds(300);
		private RSAKey key = SIGNING_KEY;

		public Builder subject(UUID subject) {
			this.subject = subject;
			return this;
		}

		public Builder email(String email) {
			this.email = email;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder username(String username) {
			this.username = username;
			return this;
		}

		public Builder roles(String... roles) {
			this.roles = List.of(roles);
			return this;
		}

		public Builder audience(String... audience) {
			this.audience = List.of(audience);
			return this;
		}

		public Builder issuer(String issuer) {
			this.issuer = issuer;
			return this;
		}

		public Builder authTime(Instant authTime) {
			this.authTime = authTime;
			return this;
		}

		public Builder expiresAt(Instant expiresAt) {
			this.expiresAt = expiresAt;
			return this;
		}

		/** Ký bằng khoá không có trong JWKS: giả mạo chữ ký. */
		public Builder signedByUnknownKey() {
			this.key = OTHER_KEY;
			return this;
		}

		/** Giá trị cho header {@code Authorization}. */
		public String bearer() {
			return "Bearer " + build();
		}

		public String build() {
			try {
				JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject.toString())
						.audience(audience).claim("email", email).claim("name", name)
						.claim("preferred_username", username)
						.claim("realm_access", java.util.Map.of("roles", roles))
						.claim("auth_time", Date.from(authTime))
						.issueTime(Date.from(Instant.now())).expirationTime(Date.from(expiresAt)).build();
				SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
						claims);
				jwt.sign(new RSASSASigner(key));
				return jwt.serialize();
			}
			catch (JOSEException e) {
				throw new IllegalStateException(e);
			}
		}

	}

	private static RSAKey newKey(String id) {
		try {
			return new RSAKeyGenerator(2048).keyID(id).generate();
		}
		catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	private static WireMockServer startJwksServer() {
		WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		server.stubFor(get(urlEqualTo(JWKS_PATH)).willReturn(aResponse().withHeader("Content-Type", "application/json")
				.withBody(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
		Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
		return server;
	}

}
