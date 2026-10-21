package com.ndz.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình của module catalog.
 *
 * @param defaultPlatformFeeBps mức phí nền tảng dùng khi Admin chưa đặt mức nào (BR-06: mặc định 5% = 500 bps)
 * @param maxPlatformFeeBps mức phí cao nhất Admin được đặt (20% = 2000 bps)
 */
@ConfigurationProperties("tickethub.catalog")
public record CatalogProperties(@DefaultValue("500") int defaultPlatformFeeBps,
		@DefaultValue("2000") int maxPlatformFeeBps) {
}
