package com.ndz.queue.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình phòng chờ (overview.md §7.1). {@code max_active} và tốc độ gọi là cấu hình chung của hệ thống, không phải
 * của từng sự kiện.
 *
 * @param openBefore phòng chờ mở trước {@code sale_starts_at} chừng này (BR-04: 15 phút)
 * @param admitPerSecond số người gọi vào tối đa mỗi giây; cũng dùng để ước tính thời gian chờ
 * @param maxActive số người tối đa đang ở trong cùng lúc: lượt vào còn hạn + booking chờ thanh toán
 * @param admissionTtl hạn của một lượt vào (BR-04: 15 phút)
 */
@ConfigurationProperties("tickethub.queue")
public record QueueProperties(@DefaultValue("15m") Duration openBefore, @DefaultValue("20") int admitPerSecond,
		@DefaultValue("200") int maxActive, @DefaultValue("15m") Duration admissionTtl) {
}
