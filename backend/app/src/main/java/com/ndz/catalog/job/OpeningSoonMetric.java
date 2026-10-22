package com.ndz.catalog.job;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.catalog.entity.enums.EventStatus;
import com.ndz.catalog.repository.EventRepository;

/**
 * Metric {@code tickethub.events.opening_soon}: số sự kiện đã publish sẽ mở bán trong khoảng tới (mặc định 15 phút).
 * KEDA đọc metric này để giữ nhiều bản sao hơn trước giờ mở bán, kịp khởi động trước khi khách đổ vào (overview.md §17,
 * ADR 0028). Mỗi pod tự cập nhật bản của mình nên không cần ShedLock; giá trị được giữ lại giữa các lần cập nhật để
 * Prometheus scrape không chạm database.
 */
@Component
public class OpeningSoonMetric {

	private final EventRepository events;
	private final Clock clock;
	private final Duration window;
	private final AtomicInteger openingSoon = new AtomicInteger();

	OpeningSoonMetric(EventRepository events, Clock clock, MeterRegistry metrics,
			@Value("${tickethub.catalog.opening-soon-window:15m}") Duration window) {
		this.events = events;
		this.clock = clock;
		this.window = window;
		Gauge.builder("tickethub.events.opening_soon", openingSoon, AtomicInteger::get)
				.description("Sự kiện sắp mở bán trong khoảng tới (KEDA pre-warm)").register(metrics);
	}

	@Scheduled(fixedDelayString = "${tickethub.catalog.opening-soon-interval:30s}")
	public void refresh() {
		var now = clock.instant();
		openingSoon.set(Math.toIntExact(
				events.countByStatusAndSaleStartsAtBetween(EventStatus.PUBLISHED, now, now.plus(window))));
	}

}
