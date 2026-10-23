package com.ndz.gateway.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token bucket phân tán trên Redis (Bucket4j). Redis lỗi thì <b>fail-open</b>: bỏ qua giới hạn, ghi log, đếm metric
 * để alert (overview.md §15.2). Fail-closed sẽ khoá cả hệ thống khi Redis chết, tệ hơn việc tạm thời không giới hạn.
 */
public class RateLimiter {

	private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

	private static final long LOG_EVERY_NANOS = Duration.ofSeconds(10).toNanos();

	/** Kết quả một lần kiểm tra; {@code retryAfterSeconds} chỉ có nghĩa khi bị từ chối. */
	public record Decision(boolean allowed, long retryAfterSeconds) {

		static final Decision ALLOWED = new Decision(true, 0);

	}

	private final RedisClient client;
	private final RateLimitProperties properties;
	private final Counter bypassed;

	private final Object connectionLock = new Object();
	private volatile ProxyManager<byte[]> proxyManager;
	private final AtomicLong bypassUntilNanos = new AtomicLong(System.nanoTime());
	private final AtomicLong lastLogNanos = new AtomicLong(System.nanoTime() - LOG_EVERY_NANOS);

	public RateLimiter(RedisClient client, RateLimitProperties properties, MeterRegistry metrics) {
		this.client = client;
		this.properties = properties;
		this.bypassed = Counter.builder("tickethub.gateway.ratelimit.bypassed")
				.description("Request bỏ qua giới hạn vì Redis lỗi (fail-open)").register(metrics);
	}

	/** Lấy một token của bucket {@code key}: {@code limit} token mỗi {@code period}, nạp dần đều. */
	public Decision tryConsume(String key, int limit, Duration period) {
		if (System.nanoTime() - bypassUntilNanos.get() < 0) {
			bypassed.increment();
			return Decision.ALLOWED;
		}
		try {
			BucketConfiguration configuration = BucketConfiguration.builder()
					.addLimit(rule -> rule.capacity(limit).refillGreedy(limit, period)).build();
			Bucket bucket = proxyManager().builder().build(key.getBytes(StandardCharsets.UTF_8), () -> configuration);
			ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
			if (probe.isConsumed()) {
				return Decision.ALLOWED;
			}
			long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).plusMillis(999).toSeconds());
			return new Decision(false, seconds);
		}
		catch (RuntimeException e) {
			return bypass(e);
		}
	}

	private Decision bypass(RuntimeException cause) {
		bypassed.increment();
		long now = System.nanoTime();
		bypassUntilNanos.set(now + properties.bypassCooldown().toNanos());
		if (now - lastLogNanos.get() >= LOG_EVERY_NANOS) {
			lastLogNanos.set(now);
			log.error("Redis lỗi, bỏ qua giới hạn tốc độ trong {} (fail-open)", properties.bypassCooldown(), cause);
		}
		return Decision.ALLOWED;
	}

	/** Kết nối tạo lười: Redis chưa sẵn sàng lúc khởi động không làm Gateway không lên được. */
	private ProxyManager<byte[]> proxyManager() {
		ProxyManager<byte[]> current = proxyManager;
		if (current != null) {
			return current;
		}
		synchronized (connectionLock) {
			if (proxyManager == null) {
				StatefulRedisConnection<byte[], byte[]> connection = client.connect(ByteArrayCodec.INSTANCE);
				connection.setTimeout(properties.commandTimeout());
				proxyManager = LettuceBasedProxyManager.builderFor(connection)
						.withExpirationStrategy(ExpirationAfterWriteStrategy
								.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
						.build();
			}
			return proxyManager;
		}
	}

}
