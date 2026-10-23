package com.ndz.booking.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.ndz.booking.service.SeatHoldService;

/**
 * Trạng thái lớp Redis giữ ghế. Redis chết thì chỉ giữ ghế dừng, xem sự kiện vẫn chạy nên trạng thái là
 * {@code DEGRADED} (HTTP 200), không phải {@code DOWN}: không làm pod bị đưa ra khỏi vòng phục vụ.
 */
@Component("seatHold")
class SeatHoldHealthIndicator implements HealthIndicator {

	static final Status DEGRADED = new Status("DEGRADED");

	private final SeatHoldService seatHolds;

	SeatHoldHealthIndicator(SeatHoldService seatHolds) {
		this.seatHolds = seatHolds;
	}

	@Override
	public Health health() {
		Health.Builder builder = seatHolds.isAvailable() ? Health.up() : Health.status(DEGRADED);
		return builder.withDetail("enabled", seatHolds.isEnabled()).build();
	}

}
