package com.ndz.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cách `app` kiểm token của Keycloak (overview.md §13.1, §13.3). {@code issuerUri} là giá trị claim {@code iss} mong
 * đợi; {@code jwkSetUri} tách riêng vì địa chỉ nội bộ để lấy khoá có thể khác địa chỉ công khai trong {@code iss}.
 */
@ConfigurationProperties("tickethub.security")
public record ResourceServerProperties(String issuerUri, String jwkSetUri,
		@DefaultValue("tickethub-api") String audience) {
}
