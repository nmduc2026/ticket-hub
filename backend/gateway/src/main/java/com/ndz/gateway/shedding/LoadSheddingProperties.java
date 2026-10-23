package com.ndz.gateway.shedding;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Load shedding ở Gateway (overview.md §15.3, ADR 0028): quá tải thì từ chối sớm bằng {@code 503 OVERLOADED} thay vì để
 * hàng đợi dài làm mọi request đều chậm và dồn xuống database.
 *
 * @param enabled tắt thì không từ chối gì
 * @param maxInFlight ngưỡng số request đang xử lý (không tính SSE); vượt thì từ chối request ghi
 * @param maxInFlightReads ngưỡng thấp hơn cho API đọc, để API đọc bị từ chối trước và chừa chỗ cho đặt vé
 * @param retryAfter gợi ý client thử lại sau chừng này (header {@code Retry-After})
 */
@ConfigurationProperties("tickethub.gateway.load-shedding")
public record LoadSheddingProperties(@DefaultValue("true") boolean enabled, @DefaultValue("500") int maxInFlight,
		@DefaultValue("350") int maxInFlightReads, @DefaultValue("2s") Duration retryAfter) {
}
