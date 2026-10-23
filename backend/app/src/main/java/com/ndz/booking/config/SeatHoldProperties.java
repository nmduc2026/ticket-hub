package com.ndz.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình lớp 1 giữ ghế bằng Redis (overview.md §10).
 *
 * @param enabled bật lớp Redis; tắt thì chỉ còn lớp Postgres (nguồn quyết định), dùng để đo baseline và khi cần gỡ
 *        Redis ra khỏi đường giữ ghế
 * @param ttlMargin key sống lâu hơn hạn giữ ghế trong Postgres chừng này để Postgres luôn là bên quyết định (30 giây)
 * @param probeInterval chu kỳ kiểm tra Redis còn sống và còn dữ liệu key; dựng lại key khi Redis vừa phục hồi
 * @param retryAfter gợi ý client thử lại sau chừng này khi Redis không dùng được (header {@code Retry-After})
 */
@ConfigurationProperties("tickethub.booking.seat-hold")
public record SeatHoldProperties(@DefaultValue("false") boolean enabled, @DefaultValue("30s") Duration ttlMargin,
		@DefaultValue("3s") Duration probeInterval, @DefaultValue("5s") Duration retryAfter) {
}
