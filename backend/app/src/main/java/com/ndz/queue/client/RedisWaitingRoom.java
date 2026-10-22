package com.ndz.queue.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

	/**
	 * Gọi người vào một lần trọn vẹn (pod chết giữa chừng không làm mất chỗ): bỏ phần tử quá hạn của {@code admitted} và
	 * {@code pending}, tính chỗ trống dưới {@code max_active}, rồi với mỗi người lấy khỏi hàng thì đặt key lượt vào
	 * (giá trị = giờ hết hạn, epoch millis) và ghi vào {@code admitted}. Trả danh sách userId vừa được gọi.
	 * <p>
	 * KEYS: waiting, admitted, pending. ARGV: bây giờ, max_active, số người tối đa, giờ hết hạn lượt vào, TTL (ms),
	 * tiền tố key lượt vào.
	 */
	private static final RedisScript<List> ADMIT = new DefaultRedisScript<>("""
			redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
			redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[1])
			local active = redis.call('ZCARD', KEYS[2]) + redis.call('ZCARD', KEYS[3])
			local count = math.min(tonumber(ARGV[2]) - active, tonumber(ARGV[3]))
			local users = {}
			if count <= 0 then return users end
			local popped = redis.call('ZPOPMIN', KEYS[1], count)
			for i = 1, #popped, 2 do
			  redis.call('SET', ARGV[6] .. popped[i], ARGV[4], 'PX', ARGV[5])
			  redis.call('ZADD', KEYS[2], ARGV[4], popped[i])
			  users[#users + 1] = popped[i]
			end
			return users
			""", List.class);

	/** Tập sự kiện đang có phòng chờ để scheduler biết phải duyệt sự kiện nào; không cần cùng slot với key của sự kiện. */
	static final String OPEN_EVENTS_KEY = "queue:open-events";

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

	/** Đánh dấu sự kiện có phòng chờ để scheduler duyệt tới. */
	public void markOpen(UUID eventId) {
		redis.opsForSet().add(OPEN_EVENTS_KEY, eventId.toString());
	}

	public Set<UUID> openEvents() {
		Set<String> members = redis.opsForSet().members(OPEN_EVENTS_KEY);
		return members == null ? Set.of() : members.stream().map(UUID::fromString).collect(Collectors.toSet());
	}

	public void markClosed(UUID eventId) {
		redis.opsForSet().remove(OPEN_EVENTS_KEY, eventId.toString());
	}

	/**
	 * Gọi tối đa {@code batch} người từ đầu hàng chờ vào khi còn chỗ dưới {@code maxActive}.
	 *
	 * @return những người vừa được gọi, theo thứ tự trong hàng
	 */
	@SuppressWarnings("unchecked")
	public List<UUID> admit(UUID eventId, long nowMillis, int maxActive, int batch, Duration ttl) {
		List<String> users = redis.execute(ADMIT,
				List.of(waitingKey(eventId), admittedKey(eventId), pendingKey(eventId)), Long.toString(nowMillis),
				Integer.toString(maxActive), Integer.toString(batch), Long.toString(nowMillis + ttl.toMillis()),
				Long.toString(ttl.toMillis()), "queue:{" + eventId + "}:admit:");
		return users.stream().map(UUID::fromString).toList();
	}

	/** Booking chờ thanh toán, tính vào số người đang ở trong tới {@code countedUntil} (hạn dài nhất có thể). */
	public void addPending(UUID eventId, UUID bookingId, Instant countedUntil) {
		redis.opsForZSet().add(pendingKey(eventId), bookingId.toString(), countedUntil.toEpochMilli());
	}

	public void removePending(UUID eventId, UUID bookingId) {
		redis.opsForZSet().remove(pendingKey(eventId), bookingId.toString());
	}

	/** Thứ hạng trong hàng chờ tính từ 0; rỗng nếu khách không ở trong hàng. */
	public OptionalLong rank(UUID eventId, UUID userId) {
		Long rank = redis.opsForZSet().rank(waitingKey(eventId), userId.toString());
		return rank == null ? OptionalLong.empty() : OptionalLong.of(rank);
	}

	public boolean hasAdmission(UUID eventId, UUID userId) {
		return Boolean.TRUE.equals(redis.hasKey(admitKey(eventId, userId)));
	}

	/** Xoá key lượt vào và bỏ khỏi {@code admitted}: từ đây chỗ của khách do booking chờ thanh toán đếm. */
	public void consumeAdmission(UUID eventId, UUID userId) {
		redis.delete(admitKey(eventId, userId));
		redis.opsForZSet().remove(admittedKey(eventId), userId.toString());
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
		markClosed(eventId);
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
