package com.ndz.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình của module catalog.
 *
 * @param defaultPlatformFeeBps mức phí nền tảng dùng khi Admin chưa đặt mức nào (BR-06: mặc định 5% = 500 bps)
 * @param maxPlatformFeeBps mức phí cao nhất Admin được đặt (20% = 2000 bps)
 * @param administrativeUnitCacheTtl thời gian giữ cache danh mục tỉnh / xã công khai
 */
@ConfigurationProperties("tickethub.catalog")
public record CatalogProperties(@DefaultValue("500") int defaultPlatformFeeBps,
		@DefaultValue("2000") int maxPlatformFeeBps, @DefaultValue("10m") Duration administrativeUnitCacheTtl) {
}
