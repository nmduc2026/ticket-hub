package com.ndz.booking.client;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Adapter Redis của lớp 1 giữ ghế (overview.md §10). Mỗi ghế một key {@code seat:{eventId}:seatId} có giá trị là
 * booking đang giữ; hash tag {@code {eventId}} đưa mọi ghế của một sự kiện vào cùng slot nên một script Lua chạm được
 * cả nhóm. Mọi lệnh ghi là script nguyên tử; nhả key luôn so giá trị (compare-and-delete) để không xoá nhầm key của
 * booking khác. Redis chỉ là lớp chặn sớm: Postgres vẫn là nơi quyết định (BR-01).
 */
public class RedisSeatHolds {

	/** Key đánh dấu "Redis còn dữ liệu giữ ghế": mất key này nghĩa là Redis đã khởi động lại hoặc bị xoá. */
	static final String MARKER_KEY = "seat-hold:marker";

	/** Giữ cả nhóm: có một key đã tồn tại thì không đặt key nào (trả 0), ngược lại đặt hết kèm TTL (trả 1). */
	private static final RedisScript<Long> HOLD = new DefaultRedisScript<>("""
			for _, key in ipairs(KEYS) do
			  if redis.call('EXISTS', key) == 1 then return 0 end
			end
			for _, key in ipairs(KEYS) do
			  redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
			end
			return 1
			""", Long.class);

	/** Nhả các key còn thuộc booking này; trả số key đã xoá. */
	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
			local released = 0
			for _, key in ipairs(KEYS) do
			  if redis.call('GET', key) == ARGV[1] then
			    redis.call('DEL', key)
			    released = released + 1
			  end
			end
			return released
			""", Long.class);

	/** Gia hạn TTL các key còn thuộc booking này; trả số key đã gia hạn. */
	private static final RedisScript<Long> EXTEND = new DefaultRedisScript<>("""
			local extended = 0
			for _, key in ipairs(KEYS) do
			  if redis.call('GET', key) == ARGV[1] then
			    redis.call('PEXPIRE', key, ARGV[2])
			    extended = extended + 1
			  end
			end
			return extended
			""", Long.class);

	private final StringRedisTemplate redis;

	public RedisSeatHolds(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** @return true nếu giữ được cả nhóm; false nếu có ghế đang bị giữ (không key nào được đặt) */
	public boolean hold(UUID eventId, List<UUID> seatIds, UUID bookingId, Duration ttl) {
		Long result = redis.execute(HOLD, keys(eventId, seatIds), bookingId.toString(), millis(ttl));
		return Long.valueOf(1).equals(result);
	}

	public void release(UUID eventId, List<UUID> seatIds, UUID bookingId) {
		redis.execute(RELEASE, keys(eventId, seatIds), bookingId.toString());
	}

	public void extend(UUID eventId, List<UUID> seatIds, UUID bookingId, Duration ttl) {
		redis.execute(EXTEND, keys(eventId, seatIds), bookingId.toString(), millis(ttl));
	}

	/** Dựng lại key từ Postgres: chỉ đặt khi key chưa có, không đè key của booking đang sống. */
	public void restore(UUID eventId, UUID seatId, UUID bookingId, Duration ttl) {
		redis.opsForValue().setIfAbsent(key(eventId, seatId), bookingId.toString(), ttl);
	}

	public boolean exists(UUID eventId, UUID seatId) {
		return Boolean.TRUE.equals(redis.hasKey(key(eventId, seatId)));
	}

	/** Gửi PING; ném lỗi nếu Redis không trả lời kịp. */
	public void ping() {
		redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
	}

	public boolean markerPresent() {
		return Boolean.TRUE.equals(redis.hasKey(MARKER_KEY));
	}

	public void writeMarker() {
		redis.opsForValue().set(MARKER_KEY, "1");
	}

	static String key(UUID eventId, UUID seatId) {
		return "seat:{" + eventId + "}:" + seatId;
	}

	private static List<String> keys(UUID eventId, List<UUID> seatIds) {
		return seatIds.stream().map(seatId -> key(eventId, seatId)).toList();
	}

	private static String millis(Duration ttl) {
		return String.valueOf(Math.max(ttl.toMillis(), 1));
	}

}
