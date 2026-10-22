package com.ndz.catalog.client;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Khoá sửa sơ đồ ghế {@code lock:seatmap:{id}} (overview.md §9): chỉ một Admin lưu một sơ đồ tại một thời điểm.
 * Đặt bằng {@code SET NX PX} kèm token, nhả bằng compare-and-delete để không xoá nhầm khoá của người khác. Việc lưu
 * có giới hạn kích thước nên chạy xong trong vài giây; TTL là lưới an toàn khi pod chết giữa chừng, không gia hạn.
 */
@Component
public class SeatMapLock {

	static final Duration TTL = Duration.ofSeconds(60);

	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
			if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;

	SeatMapLock(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** @return token để nhả khoá; {@code null} nếu người khác đang giữ khoá */
	public String tryAcquire(UUID seatMapId) {
		String token = UUID.randomUUID().toString();
		return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(seatMapId), token, TTL)) ? token : null;
	}

	public void release(UUID seatMapId, String token) {
		redis.execute(RELEASE, List.of(key(seatMapId)), token);
	}

	private static String key(UUID seatMapId) {
		return "lock:seatmap:" + seatMapId;
	}

}
