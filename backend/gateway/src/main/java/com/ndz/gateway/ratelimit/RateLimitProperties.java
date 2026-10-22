package com.ndz.gateway.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Giới hạn tốc độ ở Gateway (overview.md §13.4). User đã đăng nhập bị giới hạn theo tài khoản, request chưa đăng nhập
 * theo IP; không giới hạn theo IP với user đã đăng nhập (JMeter chạy từ một máy, khách 4G đi chung IP qua CGNAT).
 *
 * @param enabled tắt thì không giới hạn gì
 * @param commandTimeout Redis không trả lời kịp chừng này thì bỏ qua giới hạn cho request đó (fail-open)
 * @param bypassCooldown sau một lần Redis lỗi, bỏ qua giới hạn ngay (không chờ timeout từng request) trong chừng này
 * @param bookingsPerMinute {@code POST /bookings} của user đã đăng nhập (khách hay gặp SEAT_UNAVAILABLE rồi chọn lại)
 * @param queueJoinPerMinute {@code POST /queue/{eventId}/join} của user đã đăng nhập
 * @param userPerSecond mọi API còn lại của user đã đăng nhập
 * @param anonymousPerSecond mọi API của request chưa đăng nhập, theo IP
 */
@ConfigurationProperties("tickethub.gateway.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("200ms") Duration commandTimeout, @DefaultValue("5s") Duration bypassCooldown,
		@DefaultValue("10") int bookingsPerMinute, @DefaultValue("20") int queueJoinPerMinute,
		@DefaultValue("50") int userPerSecond, @DefaultValue("20") int anonymousPerSecond) {
}
