package com.ndz.account.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.ndz.account.config.AccountProperties;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Adapter gọi Keycloak Admin API bằng service account của client {@code tickethub-account-admin}, chỉ có quyền
 * {@code view-users} / {@code manage-users} (overview.md §13.2). Domain không thấy kiểu của Keycloak. Mọi lệnh
 * idempotent: gán role đã có, đặt {@code enabled} đã đúng giá trị, đăng xuất user không còn session đều không lỗi.
 * Không gọi trong transaction DB đang giữ khoá.
 */
public class KeycloakAdminClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

	private static final ParameterizedTypeReference<List<Map<String, Object>>> ROLE_LIST = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};
	/** Làm mới token sớm hơn hạn chút để request đang bay không bị từ chối. */
	private static final Duration TOKEN_MARGIN = Duration.ofSeconds(10);

	private final RestClient rest;
	private final AccountProperties.Keycloak config;
	private final Clock clock;

	private volatile CachedToken token;

	public KeycloakAdminClient(RestClient rest, AccountProperties.Keycloak config, Clock clock) {
		this.rest = rest;
		this.config = config;
		this.clock = clock;
	}

	/** Gán realm role cho user nếu chưa có. */
	public void grantRealmRole(UUID userId, String roleName) {
		call("gán role " + roleName, () -> {
			String mappings = userUrl(userId) + "/role-mappings/realm";
			if (roleNames(mappings).contains(roleName)) {
				return null;
			}
			// `available` liệt kê role còn gán được kèm id, nên chỉ cần view-users / manage-users (không cần view-realm).
			Map<String, Object> role = get(mappings + "/available", ROLE_LIST).stream()
					.filter(candidate -> roleName.equals(candidate.get("name"))).findFirst()
					.orElseThrow(() -> new IllegalStateException("Realm không có role " + roleName));
			rest.post().uri(mappings).headers(headers -> headers.setBearerAuth(accessToken()))
					.contentType(MediaType.APPLICATION_JSON).body(List.of(Map.of("id", role.get("id"), "name", roleName)))
					.retrieve().toBodilessEntity();
			return null;
		});
	}

	/** Bật / tắt đăng nhập của user. Tắt không tự thu hồi session đang có: gọi thêm {@link #logoutAllSessions}. */
	public void setEnabled(UUID userId, boolean enabled) {
		call("đặt enabled=" + enabled, () -> {
			// Gửi lại cả bản ghi vừa đọc: Keycloak kiểm tra các trường bắt buộc của user profile khi cập nhật.
			Map<String, Object> user = new HashMap<>(get(userUrl(userId), OBJECT));
			user.put("enabled", enabled);
			rest.put().uri(userUrl(userId)).headers(headers -> headers.setBearerAuth(accessToken()))
					.contentType(MediaType.APPLICATION_JSON).body(user).retrieve().toBodilessEntity();
			return null;
		});
	}

	/** Đăng xuất mọi session của user: refresh token đã cấp mất hiệu lực. */
	public void logoutAllSessions(UUID userId) {
		call("đăng xuất mọi session", () -> {
			rest.post().uri(userUrl(userId) + "/logout").headers(headers -> headers.setBearerAuth(accessToken()))
					.retrieve().toBodilessEntity();
			return null;
		});
	}

	private List<String> roleNames(String mappingsUrl) {
		return get(mappingsUrl, ROLE_LIST).stream().map(role -> (String) role.get("name")).toList();
	}

	private <T> T get(String url, ParameterizedTypeReference<T> type) {
		return rest.get().uri(url).headers(headers -> headers.setBearerAuth(accessToken())).retrieve().body(type);
	}

	private String userUrl(UUID userId) {
		return "/admin/realms/" + config.realm() + "/users/" + userId;
	}

	/** Dịch lỗi mạng / HTTP của Keycloak sang lỗi nghiệp vụ; 404 của user là {@code USER_NOT_FOUND}. */
	private <T> T call(String action, Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))) {
				throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản");
			}
			log.error("Keycloak từ chối lệnh '{}': {}", action, e.getStatusCode());
			this.token = null;
			throw unavailable(e);
		}
		catch (RestClientException e) {
			log.error("Không gọi được Keycloak để '{}'", action, e);
			throw unavailable(e);
		}
	}

	private static BusinessException unavailable(Exception cause) {
		BusinessException exception = new BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
				"Dịch vụ tài khoản tạm thời không khả dụng, vui lòng thử lại");
		exception.initCause(cause);
		return exception;
	}

	private String accessToken() {
		CachedToken cached = token;
		Instant now = clock.instant();
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return cached.value();
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", config.clientId());
		form.add("client_secret", config.clientSecret());
		Map<String, Object> response = rest.post()
				.uri("/realms/" + config.realm() + "/protocol/openid-connect/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(OBJECT);
		long expiresIn = ((Number) response.get("expires_in")).longValue();
		CachedToken fresh = new CachedToken((String) response.get("access_token"),
				now.plusSeconds(expiresIn).minus(TOKEN_MARGIN));
		this.token = fresh;
		return fresh.value();
	}

	private record CachedToken(String value, Instant expiresAt) {
	}

}
