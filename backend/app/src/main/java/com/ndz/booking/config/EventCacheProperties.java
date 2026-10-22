package com.ndz.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cache chi tiết sự kiện {@code cache:event:{eventId}} (overview.md §9, chống cache stampede).
 *
 * @param ttl thời gian bản cache còn mới (5 phút); hết thì một request rebuild, các request khác trả bản cũ
 * @param jitter cộng thêm ngẫu nhiên tới chừng này vào {@code ttl} để các sự kiện không hết hạn cùng lúc
 * @param staleFor bản cũ còn trong Redis thêm chừng này sau khi hết hạn, để trả cho request không giữ được khoá
 * @param lockTtl khoá rebuild tự nhả sau chừng này nếu pod giữ khoá chết giữa chừng
 * @param waitTimeout cache trống và có người đang rebuild: chờ tối đa chừng này rồi tự đọc DB
 * @param pollInterval chu kỳ kiểm tra cache khi chờ
 */
@ConfigurationProperties("tickethub.booking.event-cache")
public record EventCacheProperties(@DefaultValue("5m") Duration ttl, @DefaultValue("30s") Duration jitter,
		@DefaultValue("1m") Duration staleFor, @DefaultValue("10s") Duration lockTtl,
		@DefaultValue("2s") Duration waitTimeout, @DefaultValue("20ms") Duration pollInterval) {
}
