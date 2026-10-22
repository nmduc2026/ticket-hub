package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.client.RedisEventDetailCache;
import com.ndz.booking.client.RedisEventDetailCache.Entry;
import com.ndz.booking.config.EventCacheProperties;
import com.ndz.booking.service.PublicEventService;
import com.ndz.booking.dto.response.EventViews.EventDetail;
import com.ndz.booking.dto.response.EventViews.EventPage;
import com.ndz.booking.dto.response.EventViews.EventPageView;
import com.ndz.booking.dto.response.EventViews.SeatMapState;
import com.ndz.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.booking.dto.response.EventViews.TierAvailability;
import com.ndz.booking.repository.PublicEventQueries;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Đọc công khai về sự kiện cho khách chưa đăng nhập (overview.md §11.2). Sơ đồ ghế lấy từ Catalog qua interface công
 * khai (ADR 0018), trạng thái ghế từ tồn kho của Booking.
 */
@Service
public class PublicEventServiceImpl implements PublicEventService {

	private static final Logger log = LoggerFactory.getLogger(PublicEventServiceImpl.class);

	private static final int MAX_PAGE_SIZE = 50;

	private final PublicEventQueries queries;
	private final CatalogApi catalog;
	private final Clock clock;
	private final RedisEventDetailCache cache;
	private final EventCacheProperties cacheProperties;
	private final MeterRegistry metrics;

	PublicEventServiceImpl(PublicEventQueries queries, CatalogApi catalog, Clock clock, RedisEventDetailCache cache,
			EventCacheProperties cacheProperties, MeterRegistry metrics) {
		this.queries = queries;
		this.catalog = catalog;
		this.clock = clock;
		this.cache = cache;
		this.cacheProperties = cacheProperties;
		this.metrics = metrics;
	}

	@Override
	@Transactional(readOnly = true)
	public EventPage search(SearchCriteria criteria, int page, int size) {
		return queries.search(criteria, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				clock.instant());
	}

	/**
	 * Chi tiết sự kiện qua cache (overview.md §9): bản còn mới trả ngay; hết hạn thì một request giữ khoá rebuild, các
	 * request khác trả bản cũ; cache trống thì một request đọc DB, các request khác chờ nó rồi đọc cache. Redis lỗi thì
	 * đọc thẳng DB, cache không bao giờ làm hỏng trang chi tiết. Số lượng vé còn có thể cũ tới hết {@code ttl}; sơ đồ
	 * ghế ({@link #seats}) luôn đọc thẳng.
	 */
	@Override
	public EventPageView detail(UUID eventId) {
		try {
			return cachedDetail(eventId);
		}
		catch (DataAccessException e) {
			log.warn("Cache chi tiết sự kiện {} không dùng được, đọc thẳng DB", eventId, e);
			count("bypass");
			return loadDetail(eventId);
		}
	}

	private EventPageView cachedDetail(UUID eventId) {
		Optional<Entry> cached = cache.read(eventId);
		if (cached.isPresent() && cached.get().isFresh(clock.instant())) {
			count("hit");
			return cached.get().view();
		}
		Optional<String> lock = cache.tryLock(eventId, cacheProperties.lockTtl());
		if (lock.isPresent()) {
			try {
				// Request khác có thể vừa rebuild xong giữa lúc ta đọc cache và giành khoá: đọc lại để không đọc DB hai lần.
				Optional<Entry> rebuilt = cache.read(eventId);
				if (rebuilt.isPresent() && rebuilt.get().isFresh(clock.instant())) {
					count("hit");
					return rebuilt.get().view();
				}
				return rebuild(eventId);
			}
			finally {
				cache.unlock(eventId, lock.get());
			}
		}
		if (cached.isPresent()) {
			count("stale");
			return cached.get().view();
		}
		return waitForRebuild(eventId);
	}

	private EventPageView rebuild(UUID eventId) {
		count("rebuild");
		EventPageView view = loadDetail(eventId);
		Duration jitter = Duration
				.ofMillis(ThreadLocalRandom.current().nextLong(cacheProperties.jitter().toMillis() + 1));
		Instant freshUntil = clock.instant().plus(cacheProperties.ttl()).plus(jitter);
		cache.write(eventId, new Entry(freshUntil, view),
				cacheProperties.ttl().plus(jitter).plus(cacheProperties.staleFor()));
		return view;
	}

	/** Cache trống và đang có request khác rebuild: chờ bản của nó; quá hạn chờ thì tự đọc DB (không ghi cache). */
	private EventPageView waitForRebuild(UUID eventId) {
		long deadline = System.nanoTime() + cacheProperties.waitTimeout().toNanos();
		while (System.nanoTime() < deadline) {
			sleep(cacheProperties.pollInterval());
			Optional<Entry> cached = cache.read(eventId);
			if (cached.isPresent()) {
				count("wait");
				return cached.get().view();
			}
		}
		count("wait_timeout");
		return loadDetail(eventId);
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Bị ngắt khi chờ cache chi tiết sự kiện", e);
		}
	}

	private void count(String result) {
		metrics.counter("tickethub.booking.event_cache", "result", result).increment();
	}

	private EventPageView loadDetail(UUID eventId) {
		EventDetail event = find(eventId);
		List<TierAvailability> tiers = queries.tiers(eventId);
		// "Hết vé" không lưu trong DB: tính từ số ghế còn trống, vì hoàn vé có thể làm ghế trống trở lại (§7.7).
		boolean soldOut = !tiers.isEmpty() && tiers.stream().allMatch(tier -> tier.available() == 0);
		return new EventPageView(event, tiers, soldOut);
	}

	@Override
	@Transactional(readOnly = true)
	public SeatMapState seats(UUID eventId) {
		EventDetail event = find(eventId);
		return new SeatMapState(event, catalog.seatMapLayout(event.seatMapId()), queries.tiers(eventId),
				queries.tierBySectionCode(eventId), queries.seatStates(eventId));
	}

	private EventDetail find(UUID eventId) {
		return queries.detail(eventId).orElseThrow(
				() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
	}

}
