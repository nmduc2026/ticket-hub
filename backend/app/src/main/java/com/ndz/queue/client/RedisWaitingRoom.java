package com.ndz.queue.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Adapter Redis của phòng chờ (overview.md §7.1, §9). Mọi key của một sự kiện có hash tag {@code {eventId}} để cùng
 * slot nếu chuyển sang Redis Cluster (Lua chỉ chạy được trên key cùng slot). Hàng chờ và lượt vào chỉ là trạng thái
 * tạm: mất đi thì khách xếp hàng lại, không ảnh hưởng tới việc không bán trùng ghế (BR-01).
 */
public class RedisWaitingRoom {

	/** Kết quả của {@link #join} khi khách đã có lượt vào còn hạn. */
	public static final long ALREADY_ADMITTED = -1;

	/**
	 * Nếu đã có lượt vào thì trả -1, không xếp lại. Ngược lại {@code ZADD NX} (vào lại không đổi chỗ) rồi trả thứ hạng
	 * tính từ 0.
	 */
	private static final RedisScript<Long> JOIN = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 1 then return -1 end
			redis.call('ZADD', KEYS[2], 'NX', ARGV[2], ARGV[1])
			return redis.call('ZRANK', KEYS[2], ARGV[1])
			""", Long.class);

	private static final String HIGH_DEMAND = "high_demand";
	private static final String SALE_STARTS_AT = "sale_starts_at";

	private final StringRedisTemplate redis;

	public RedisWaitingRoom(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** Thông tin sự kiện Queue cần; {@code saleStartsAt} là mốc chia "trước / sau giờ mở bán" (BR-04). */
	public record Meta(boolean highDemand, Instant saleStartsAt) {
	}

	public void writeMeta(UUID eventId, Meta meta) {
		redis.opsForHash().putAll(metaKey(eventId), Map.of(HIGH_DEMAND, Boolean.toString(meta.highDemand()),
				SALE_STARTS_AT, Long.toString(meta.saleStartsAt().toEpochMilli())));
	}

	public Optional<Meta> meta(UUID eventId) {
		Map<Object, Object> entries = redis.opsForHash().entries(metaKey(eventId));
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Meta(Boolean.parseBoolean((String) entries.get(HIGH_DEMAND)),
				Instant.ofEpochMilli(Long.parseLong((String) entries.get(SALE_STARTS_AT)))));
	}

	/** @return {@link #ALREADY_ADMITTED}, hoặc thứ hạng trong hàng chờ tính từ 0 */
	public long join(UUID eventId, UUID userId, double score) {
		return redis.execute(JOIN, List.of(admitKey(eventId, userId), waitingKey(eventId)), userId.toString(),
				plain(score));
	}

	/** Thứ hạng trong hàng chờ tính từ 0; rỗng nếu khách không ở trong hàng. */
	public OptionalLong rank(UUID eventId, UUID userId) {
		Long rank = redis.opsForZSet().rank(waitingKey(eventId), userId.toString());
		return rank == null ? OptionalLong.empty() : OptionalLong.of(rank);
	}

	/** Giờ hết hạn lượt vào (giá trị key là epoch millis); rỗng nếu khách không có lượt vào còn hạn. */
	public Optional<Instant> admission(UUID eventId, UUID userId) {
		String expiresAtMillis = redis.opsForValue().get(admitKey(eventId, userId));
		return Optional.ofNullable(expiresAtMillis).map(value -> Instant.ofEpochMilli(Long.parseLong(value)));
	}

	/** Xoá mọi key {@code queue:{eventId}:*}, kể cả lượt vào của từng khách (liệt kê qua {@code admitted}). */
	public void close(UUID eventId) {
		Set<String> admittedUsers = redis.opsForZSet().range(admittedKey(eventId), 0, -1);
		if (admittedUsers != null && !admittedUsers.isEmpty()) {
			redis.delete(admittedUsers.stream().map(user -> admitKey(eventId, UUID.fromString(user))).toList());
		}
		redis.delete(List.of(waitingKey(eventId), admittedKey(eventId), pendingKey(eventId), metaKey(eventId)));
	}

	public static String waitingKey(UUID eventId) {
		return "queue:{" + eventId + "}:waiting";
	}

	public static String admittedKey(UUID eventId) {
		return "queue:{" + eventId + "}:admitted";
	}

	public static String pendingKey(UUID eventId) {
		return "queue:{" + eventId + "}:pending";
	}

	public static String metaKey(UUID eventId) {
		return "queue:{" + eventId + "}:meta";
	}

	public static String admitKey(UUID eventId, UUID userId) {
		return "queue:{" + eventId + "}:admit:" + userId;
	}

	/** Score epoch-millis cỡ 1,7e12 ghi dạng thập phân thường để Lua nhận đúng, không bị đổi sang số mũ. */
	private static String plain(double score) {
		return BigDecimal.valueOf(score).toPlainString();
	}

}
