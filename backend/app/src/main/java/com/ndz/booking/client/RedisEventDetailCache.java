package com.ndz.booking.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ndz.booking.dto.response.EventViews.EventPageView;

/**
 * Adapter Redis của cache chi tiết sự kiện (overview.md §9). Mỗi bản cache mang giờ "còn mới" riêng: Redis giữ nó lâu
 * hơn một chút để request không giành được khoá rebuild vẫn có bản cũ để trả. Cache chỉ là bản sao đọc nhanh, Postgres
 * là nguồn thật.
 */
public class RedisEventDetailCache {

	private static final Logger log = LoggerFactory.getLogger(RedisEventDetailCache.class);

	/** Nhả khoá chỉ khi còn là của mình (compare-and-delete). */
	private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
			if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
			return 0
			""", Long.class);

	/** Bản cache cùng giờ nó hết "mới". */
	public record Entry(Instant freshUntil, EventPageView view) {

		public boolean isFresh(Instant now) {
			return now.isBefore(freshUntil);
		}

	}

	private final StringRedisTemplate redis;
	private final ObjectMapper json;

	public RedisEventDetailCache(StringRedisTemplate redis, ObjectMapper json) {
		this.redis = redis;
		this.json = json;
	}

	/** Bản cache hiện có; bản hỏng (đổi định dạng sau khi triển khai) coi như chưa có và bị xoá. */
	public Optional<Entry> read(UUID eventId) {
		String raw = redis.opsForValue().get(key(eventId));
		if (raw == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(json.readValue(raw, Entry.class));
		}
		catch (JacksonException e) {
			log.warn("Bản cache chi tiết sự kiện {} không đọc được, xoá và đọc lại từ DB", eventId, e);
			redis.delete(key(eventId));
			return Optional.empty();
		}
	}

	public void write(UUID eventId, Entry entry, Duration redisTtl) {
		redis.opsForValue().set(key(eventId), json.writeValueAsString(entry), redisTtl);
	}

	public void evict(UUID eventId) {
		redis.delete(key(eventId));
	}

	/**
	 * Xoá cache sau khi transaction đang chạy commit: xoá trước commit thì một request khác có thể đọc bản cũ từ DB và
	 * ghi lại vào cache.
	 */
	public void evictAfterCommit(UUID eventId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			evict(eventId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evict(eventId);
			}
		});
	}

	/** @return token của khoá nếu giành được, rỗng nếu đang có request khác rebuild */
	public Optional<String> tryLock(UUID eventId, Duration ttl) {
		String token = UUID.randomUUID().toString();
		Boolean acquired = redis.opsForValue().setIfAbsent(lockKey(eventId), token, ttl);
		return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
	}

	public void unlock(UUID eventId, String token) {
		redis.execute(UNLOCK, List.of(lockKey(eventId)), token);
	}

	public static String key(UUID eventId) {
		return "cache:event:{" + eventId + "}";
	}

	public static String lockKey(UUID eventId) {
		return "lock:event-cache:{" + eventId + "}";
	}

}
