package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ndz.booking.client.RedisSeatHolds;
import com.ndz.booking.config.BookingProperties;
import com.ndz.booking.config.SeatHoldProperties;
import com.ndz.booking.dto.response.BookingViews.HeldSeat;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.service.SeatHoldService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Cài đặt lớp 1 giữ ghế (overview.md §10, §15.2). {@code ready} là trạng thái "Redis sống và key đã đủ": chỉ
 * {@link #probe} đổi nó lên true (sau khi dựng lại key), còn lỗi Redis ở bất kỳ lệnh giữ ghế nào đổi nó xuống false
 * ngay, nên các request sau trả 503 luôn thay vì chờ timeout từng cái.
 */
@Service
public class SeatHoldServiceImpl implements SeatHoldService {

	private static final Logger log = LoggerFactory.getLogger(SeatHoldServiceImpl.class);

	private final RedisSeatHolds redis;
	private final BookingRepository bookings;
	private final SeatHoldProperties properties;
	private final BookingProperties bookingProperties;
	private final Clock clock;
	private final Counter redisRejected;
	private final Counter unavailable;

	private volatile boolean enabled;
	private final AtomicBoolean ready = new AtomicBoolean(false);

	SeatHoldServiceImpl(RedisSeatHolds redis, BookingRepository bookings, SeatHoldProperties properties,
			BookingProperties bookingProperties, Clock clock, MeterRegistry metrics) {
		this.redis = redis;
		this.bookings = bookings;
		this.properties = properties;
		this.bookingProperties = bookingProperties;
		this.clock = clock;
		this.enabled = properties.enabled();
		this.redisRejected = Counter.builder("tickethub.booking.seat_hold.redis_rejected")
				.description("Số lần Redis chặn sớm vì ghế đang bị giữ, Postgres không phải chạm tới").register(metrics);
		this.unavailable = Counter.builder("tickethub.booking.seat_hold.unavailable")
				.description("Số lần từ chối giữ ghế vì Redis không dùng được (fail-closed)").register(metrics);
		Gauge.builder("tickethub.booking.seat_hold.available", this, service -> service.isAvailable() ? 1 : 0)
				.description("1 khi giữ ghế sẵn sàng, 0 khi Redis không dùng được").register(metrics);
	}

	@Override
	public void acquire(UUID eventId, List<UUID> seatIds, UUID bookingId, Instant expiresAt) {
		if (!enabled) {
			return;
		}
		if (!ready.get()) {
			throw unavailable();
		}
		boolean held;
		try {
			Duration ttl = Duration.between(clock.instant(), expiresAt).plus(properties.ttlMargin());
			held = redis.hold(eventId, seatIds, bookingId, ttl);
		}
		catch (RuntimeException e) {
			markDown(e);
			throw unavailable();
		}
		if (!held) {
			redisRejected.increment();
			throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE, "Ghế vừa có người giữ, vui lòng chọn ghế khác");
		}
	}

	@Override
	public void release(UUID eventId, List<UUID> seatIds, UUID bookingId) {
		if (!enabled || seatIds.isEmpty()) {
			return;
		}
		try {
			redis.release(eventId, seatIds, bookingId);
		}
		catch (RuntimeException e) {
			log.warn("Không nhả được key giữ ghế của booking {} (tự hết hạn theo TTL): {}", bookingId, e.toString());
		}
	}

	@Override
	public void releaseAfterCommit(UUID eventId, List<UUID> seatIds, UUID bookingId) {
		if (!enabled) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			release(eventId, seatIds, bookingId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				release(eventId, seatIds, bookingId);
			}
		});
	}

	@Override
	public void extend(UUID eventId, List<UUID> seatIds, UUID bookingId, Instant holdUntil) {
		if (!enabled || seatIds.isEmpty()) {
			return;
		}
		try {
			redis.extend(eventId, seatIds, bookingId,
					Duration.between(clock.instant(), holdUntil).plus(properties.ttlMargin()));
		}
		catch (RuntimeException e) {
			log.warn("Không gia hạn được key giữ ghế của booking {}: {}", bookingId, e.toString());
		}
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (enabled) {
			// Bật lại sau một thời gian tắt: key đã lệch với Postgres, dựng lại trước khi nhận giữ ghế.
			ready.set(false);
		}
		log.warn("Lớp Redis giữ ghế {}", enabled ? "bật" : "tắt");
	}

	@Override
	public boolean isAvailable() {
		return !enabled || ready.get();
	}

	@Override
	public void probe() {
		if (!enabled) {
			return;
		}
		try {
			redis.ping();
			if (ready.get() && redis.markerPresent()) {
				return;
			}
			rebuild();
		}
		catch (RuntimeException e) {
			markDown(e);
		}
	}

	/** Dựng lại key từ các dòng HELD rồi mới mở lại giữ ghế; key đã có (booking đang sống) được giữ nguyên. */
	private void rebuild() {
		ready.set(false);
		Instant now = clock.instant();
		List<HeldSeat> held = bookings.findHeldSeats();
		for (HeldSeat seat : held) {
			Instant holdUntil = seat.paymentStartedAt() == null ? seat.expiresAt()
					: seat.expiresAt().plus(bookingProperties.paymentGrace());
			Duration ttl = Duration.between(now, holdUntil).plus(properties.ttlMargin());
			if (!ttl.isNegative() && !ttl.isZero()) {
				redis.restore(seat.eventId(), seat.seatId(), seat.bookingId(), ttl);
			}
		}
		redis.writeMarker();
		ready.set(true);
		log.info("Redis giữ ghế sẵn sàng: dựng lại key cho {} ghế đang giữ", held.size());
	}

	private void markDown(RuntimeException cause) {
		if (ready.getAndSet(false)) {
			log.error("Redis giữ ghế không dùng được, dừng nhận giữ ghế cho tới khi phục hồi", cause);
		}
	}

	private BusinessException unavailable() {
		unavailable.increment();
		return new BusinessException(ErrorCode.SEAT_HOLD_UNAVAILABLE,
				"Hệ thống giữ ghế đang bận, vui lòng thử lại sau ít giây")
				.retryAfter((int) properties.retryAfter().toSeconds());
	}

}
