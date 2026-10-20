package com.ndz.backend.shared.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình Idempotency-Key.
 *
 * @param retention bản ghi được giữ bao lâu (BR: 24 giờ)
 * @param inProgressTimeout request {@code IN_PROGRESS} quá lâu coi như tiến trình đã chết (crash giữa chừng) và cho
 *        phép claim lại; nếu không, client retry sẽ nhận 409 cho tới khi bản ghi hết hạn
 * @param maxBodySize body lớn hơn mức này bị từ chối vì filter phải đọc hết để tính hash
 */
@ConfigurationProperties("tickethub.idempotency")
public record IdempotencyProperties(@DefaultValue("24h") Duration retention,
		@DefaultValue("2m") Duration inProgressTimeout, @DefaultValue("1048576") int maxBodySize) {
}
