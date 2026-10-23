package com.ndz;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;

/**
 * Gọi Keycloak thật của test (Testcontainers) bằng quyền admin của realm master để dựng user và kiểm kết quả mà
 * module account đã làm: role, trạng thái {@code enabled}, session; và đăng nhập / refresh token như người dùng thật.
 */
public class KeycloakTestApi {

	public static final String PASSWORD = "Test-1234";

	private static final String REALM = "tickethub";
	private static final String TEST_CLIENT = "tickethub-test";

	private final String baseUrl;
	private final HttpClient http = HttpClient.newHttpClient();
	private boolean testClientReady;

	KeycloakTestApi(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/** Tạo user có mật khẩu {@link #PASSWORD}; trả về id (khớp với {@code sub} khi user đăng nhập). */
	public UUID createUser(String username, String email) {
		String body = """
				{"username":"%s","email":"%s","emailVerified":true,"enabled":true,"firstName":"Test","lastName":"User",
				 "credentials":[{"type":"password","value":"%s","temporary":false}]}
				""".formatted(username, email, PASSWORD);
		HttpResponse<String> response = adminRequest("POST", "/admin/realms/" + REALM + "/users", body);
		if (response.statusCode() != 201) {
			throw new IllegalStateException("Không tạo được user: " + response.statusCode() + " " + response.body());
		}
		String location = response.headers().firstValue("Location").orElseThrow();
		return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
	}

	/** Realm role đang gán trực tiếp cho user. */
	public List<String> realmRoles(UUID userId) {
		String json = adminRequest("GET", "/admin/realms/" + REALM + "/users/" + userId + "/role-mappings/realm", null)
				.body();
		return JsonPath.read(json, "$[*].name");
	}

	public boolean isEnabled(UUID userId) {
		String json = adminRequest("GET", "/admin/realms/" + REALM + "/users/" + userId, null).body();
		return JsonPath.read(json, "$.enabled");
	}

	public int sessionCount(UUID userId) {
		String json = adminRequest("GET", "/admin/realms/" + REALM + "/users/" + userId + "/sessions", null).body();
		return JsonPath.<List<?>>read(json, "$").size();
	}

	/** Đăng nhập bằng mật khẩu; trả về refresh token, hoặc null nếu bị từ chối. */
	public String login(String username, String password) {
		ensureTestClient();
		HttpResponse<String> response = tokenRequest(
				Map.of("grant_type", "password", "client_id", TEST_CLIENT, "username", username, "password", password));
		return response.statusCode() == 200 ? JsonPath.read(response.body(), "$.refresh_token") : null;
	}

	/** Đăng nhập bằng mật khẩu; trả về id phiên Keycloak (claim {@code sid} của access token). */
	public String loginSessionId(String username) {
		ensureTestClient();
		HttpResponse<String> response = tokenRequest(
				Map.of("grant_type", "password", "client_id", TEST_CLIENT, "username", username, "password", PASSWORD));
		String accessToken = JsonPath.read(response.body(), "$.access_token");
		String payload = new String(java.util.Base64.getUrlDecoder().decode(accessToken.split("[.]")[1]),
				StandardCharsets.UTF_8);
		return JsonPath.read(payload, "$.sid");
	}

	/** User còn tồn tại trong Keycloak không. */
	public boolean exists(UUID userId) {
		return adminRequest("GET", "/admin/realms/" + REALM + "/users/" + userId, null).statusCode() == 200;
	}

	/** Mã HTTP khi đổi refresh token lấy access token mới: 200 còn hiệu lực, 400 đã mất hiệu lực. */
	public int refreshStatus(String refreshToken) {
		ensureTestClient();
		return tokenRequest(Map.of("grant_type", "refresh_token", "client_id", TEST_CLIENT, "refresh_token",
				refreshToken)).statusCode();
	}

	private synchronized void ensureTestClient() {
		if (testClientReady) {
			return;
		}
		String body = """
				{"clientId":"%s","publicClient":true,"directAccessGrantsEnabled":true,"standardFlowEnabled":false,
				 "protocol":"openid-connect"}
				""".formatted(TEST_CLIENT);
		int status = adminRequest("POST", "/admin/realms/" + REALM + "/clients", body).statusCode();
		if (status != 201 && status != 409) {
			throw new IllegalStateException("Không tạo được client test: " + status);
		}
		testClientReady = true;
	}

	private HttpResponse<String> tokenRequest(Map<String, String> form) {
		return send(HttpRequest.newBuilder(URI.create(baseUrl + "/realms/" + REALM + "/protocol/openid-connect/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(encode(form))).build());
	}

	private HttpResponse<String> adminRequest(String method, String path, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
				.header("Authorization", "Bearer " + adminToken());
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", "application/json").method(method,
					HttpRequest.BodyPublishers.ofString(json));
		}
		return send(request.build());
	}

	private String adminToken() {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder(URI.create(baseUrl + "/realms/master/protocol/openid-connect/token"))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.POST(HttpRequest.BodyPublishers.ofString(encode(Map.of("grant_type", "password", "client_id",
								"admin-cli", "username", TestcontainersConfiguration.KEYCLOAK_ADMIN, "password",
								TestcontainersConfiguration.KEYCLOAK_ADMIN))))
						.build());
		return JsonPath.read(response.body(), "$.access_token");
	}

	private static String encode(Map<String, String> form) {
		return form.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return http.send(request, BodyHandlers.ofString());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
