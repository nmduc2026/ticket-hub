package com.ndz.payment.client;

import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import com.ndz.payment.config.StripeProperties;

/**
 * Retry, circuit breaker và bulkhead quanh mọi lời gọi Stripe (overview.md §15.1). Thứ tự từ ngoài vào: retry →
 * breaker → bulkhead → lời gọi (timeout 3 giây đặt ở SDK), nên mỗi lần thử đều được breaker đếm.
 *
 * <p>Chỉ lỗi tạm thời (mạng, quá thời gian, 5xx: {@link StripeGatewayException} không có mã) bị đếm là lỗi và được
 * thử lại; lỗi 4xx có mã (thẻ bị từ chối, trạng thái không hợp lệ...) là lỗi nghiệp vụ nên không làm mở breaker. Retry
 * chỉ áp dụng cho lời gọi có idempotency key: gọi lại cùng key không tạo thêm tiền hoặc thêm lần hoàn.
 *
 * <p>Metric {@code resilience4j_*} (tag {@code name="stripe"}) có trên {@code /actuator/prometheus}.
 */
public class StripeResilience {

	private final Retry retry;
	private final CircuitBreaker breaker;
	private final Bulkhead bulkhead;

	public StripeResilience(StripeProperties.Resilience config, MeterRegistry metrics) {
		CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
		this.breaker = breakers.circuitBreaker("stripe", CircuitBreakerConfig.custom()
				.failureRateThreshold(config.failureRateThreshold()).slidingWindowSize(config.slidingWindowSize())
				.minimumNumberOfCalls(config.slidingWindowSize()).waitDurationInOpenState(config.openDuration())
				.permittedNumberOfCallsInHalfOpenState(config.halfOpenCalls())
				.recordException(StripeResilience::isTransient).ignoreException(e -> !isTransient(e)).build());
		BulkheadRegistry bulkheads = BulkheadRegistry.ofDefaults();
		this.bulkhead = bulkheads.bulkhead("stripe", BulkheadConfig.custom()
				.maxConcurrentCalls(config.maxConcurrentCalls()).maxWaitDuration(Duration.ZERO).build());
		RetryRegistry retries = RetryRegistry.ofDefaults();
		this.retry = retries.retry("stripe", RetryConfig.custom().maxAttempts(config.retryAttempts())
				.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(config.retryBackoff(),
						config.retryMultiplier(), config.retryJitter()))
				.retryOnException(StripeResilience::isTransient).build());
		TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(metrics);
		TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheads).bindTo(metrics);
		TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(metrics);
	}

	/**
	 * Chạy một lời gọi Stripe.
	 *
	 * @param idempotent có idempotency key: được thử lại khi lỗi tạm thời
	 * @throws StripeGatewayException lỗi từ Stripe; breaker mở hoặc bulkhead đầy cũng được báo bằng loại này (không mã)
	 */
	public <T> T execute(boolean idempotent, Supplier<T> call) {
		Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead, call);
		guarded = CircuitBreaker.decorateSupplier(breaker, guarded);
		if (idempotent) {
			guarded = Retry.decorateSupplier(retry, guarded);
		}
		try {
			return guarded.get();
		}
		catch (CallNotPermittedException e) {
			throw new StripeGatewayException("Stripe tạm ngắt: circuit breaker đang mở", null, e);
		}
		catch (BulkheadFullException e) {
			throw new StripeGatewayException("Stripe quá tải: đã đủ số lời gọi đồng thời", null, e);
		}
	}

	public CircuitBreaker.State state() {
		return breaker.getState();
	}

	/** Đưa breaker về đóng và xoá bộ đếm; dùng cho test và thao tác vận hành sau sự cố. */
	public void reset() {
		breaker.reset();
	}

	private static boolean isTransient(Throwable e) {
		return e instanceof StripeGatewayException gateway && gateway.code() == null;
	}

}
