package com.ndz.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SSE đi qua Gateway (plan Sprint 7 hạng mục 4, AC-6; overview.md §12): sự kiện app đã đẩy phải tới client ngay, không
 * bị Gateway giữ lại cho tới khi app đóng luồng. App giả đẩy một sự kiện rồi chặn tới khi test cho phép đẩy tiếp; nếu
 * Gateway buffer thì sự kiện đầu không bao giờ tới client trong lúc app còn chặn.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SseThroughGatewayTests {

	private static final String REALM = "/realms/tickethub";
	private static final String STREAM_PATH = "/api/v1/events/42/seats/stream";
	private static final CountDownLatch PUSH_SECOND_EVENT = new CountDownLatch(1);

	private static final WireMockServer IDP = startIdentityProvider();
	private static final HttpServer APP = startApp();

	@Container
	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.5").withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
				() -> "http://localhost:" + IDP.port() + REALM);
		registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> "test-secret");
		registry.add("tickethub.gateway.app-uri", () -> "http://localhost:" + APP.getAddress().getPort());
	}

	@Value("${local.server.port}")
	private int port;

	@AfterAll
	static void stop() {
		PUSH_SECOND_EVENT.countDown();
		APP.stop(0);
		IDP.stop();
	}

	@Test
	@DisplayName("AC-6: sự kiện SSE app vừa đẩy tới client ngay qua Gateway, không đợi app đóng luồng")
	void eventsReachTheClientWhileTheAppStillHoldsTheStreamOpen() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + STREAM_PATH))
				.header("Accept", "text/event-stream").build();

		HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream());
		try (BufferedReader body = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");

			// App còn chặn ở sự kiện thứ hai: dòng đầu chỉ tới được nếu Gateway chuyển tiếp từng đoạn.
			assertThat(readLine(body, Duration.ofSeconds(5))).isEqualTo("data: first");
			assertThat(PUSH_SECOND_EVENT.getCount()).isEqualTo(1);

			PUSH_SECOND_EVENT.countDown();
			assertThat(readLine(body, Duration.ofSeconds(5))).isEmpty();
			assertThat(readLine(body, Duration.ofSeconds(5))).isEqualTo("data: second");
		}
	}

	/** Đọc một dòng nhưng không chờ quá {@code timeout}, để Gateway buffer thì test hỏng thay vì treo. */
	private static String readLine(BufferedReader body, Duration timeout) throws Exception {
		var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
		try {
			return executor.submit(body::readLine).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		finally {
			executor.shutdownNow();
		}
	}

	/** App giả: đẩy "first", chặn tới khi test cho phép, rồi đẩy "second" và đóng luồng. */
	private static HttpServer startApp() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext(STREAM_PATH, exchange -> {
				exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
					out.flush();
					try {
						PUSH_SECOND_EVENT.await(30, TimeUnit.SECONDS);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					out.write("data: second\n\n".getBytes(StandardCharsets.UTF_8));
					out.flush();
				}
				catch (IOException ignored) {
					// Client đã ngắt khi test kết thúc.
				}
			});
			server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
			server.start();
			return server;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Gateway đọc discovery của Keycloak lúc khởi động; test này không đăng nhập nên chỉ cần discovery. */
	private static WireMockServer startIdentityProvider() {
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
		return server;
	}

}
