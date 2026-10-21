package com.ndz.account.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình module Account.
 *
 * @param keycloak kết nối Keycloak Admin API bằng client {@code tickethub-account-admin} (overview.md §13.2)
 */
@ConfigurationProperties("tickethub.account")
public record AccountProperties(Keycloak keycloak) {

	/**
	 * @param baseUrl địa chỉ Keycloak mà app gọi được (có thể là địa chỉ nội bộ), không kèm {@code /realms}
	 * @param clientSecret secret của client; chỉ từ biến môi trường / Secret, không commit
	 */
	public record Keycloak(@DefaultValue("http://localhost:8180") String baseUrl,
			@DefaultValue("tickethub") String realm, @DefaultValue("tickethub-account-admin") String clientId,
			@DefaultValue("") String clientSecret, @DefaultValue("5s") Duration timeout) {
	}

}
