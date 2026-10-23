package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ndz.catalog.config.CatalogProperties;
import com.ndz.catalog.dto.response.CatalogViews.UnitRow;
import com.ndz.catalog.util.AdministrativeUnitCache;

/** Cache danh mục tỉnh / xã công khai (hạng mục 15): giữ trong TTL, hết hạn thì nạp lại, evict thì nạp lại. Test thuần. */
class AdministrativeUnitCacheTests {

	private static final Instant START = Instant.parse("2026-10-22T00:00:00Z");

	private Instant now = START;
	private final Clock clock = new Clock() {
		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	};
	private final AdministrativeUnitCache cache = new AdministrativeUnitCache(
			new CatalogProperties(500, 2000, Duration.ofMinutes(10)), clock);
	private final AtomicInteger loads = new AtomicInteger();

	private List<UnitRow> load() {
		loads.incrementAndGet();
		return List.of(new UnitRow(UUID.randomUUID(), "79", "TP. Hồ Chí Minh", "PROVINCE", 1, null));
	}

	@Test
	@DisplayName("Cùng bộ lọc trong TTL chỉ đọc DB một lần")
	void servesFromCacheWithinTtl() {
		cache.get(1, null, this::load);
		now = START.plus(Duration.ofMinutes(9));
		cache.get(1, null, this::load);
		assertThat(loads).hasValue(1);
	}

	@Test
	@DisplayName("Bộ lọc khác nhau có mục cache riêng")
	void keysByLevelAndParent() {
		cache.get(1, null, this::load);
		cache.get(2, UUID.randomUUID(), this::load);
		assertThat(loads).hasValue(2);
	}

	@Test
	@DisplayName("Hết TTL thì nạp lại")
	void reloadsAfterTtl() {
		cache.get(1, null, this::load);
		now = START.plus(Duration.ofMinutes(10));
		cache.get(1, null, this::load);
		assertThat(loads).hasValue(2);
	}

	@Test
	@DisplayName("Admin đổi danh mục (ngoài transaction) thì xoá cache ngay")
	void evictClearsEntries() {
		cache.get(1, null, this::load);
		cache.evictAfterCommit();
		cache.get(1, null, this::load);
		assertThat(loads).hasValue(2);
	}

}
