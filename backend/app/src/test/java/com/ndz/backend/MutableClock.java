package com.ndz.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Đồng hồ cho test: mặc định chạy theo giờ thật; {@link #setInstant} đặt cố định một thời điểm để kiểm các nhánh theo
 * thời gian (mở bán, kết thúc, hết hạn giữ ghế) mà không phải chờ. Nhớ gọi {@link #reset()} sau mỗi test.
 */
public final class MutableClock extends Clock {

	private volatile Instant fixed;

	public void setInstant(Instant instant) {
		this.fixed = instant;
	}

	public void reset() {
		this.fixed = null;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		Instant current = fixed;
		return current != null ? current : Instant.now();
	}

}
