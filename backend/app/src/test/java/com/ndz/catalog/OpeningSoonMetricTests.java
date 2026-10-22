package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.catalog.job.OpeningSoonMetric;

/**
 * Metric pre-warm cho KEDA (plan Sprint 7 hạng mục 7, AC-11; overview.md §17): đếm sự kiện đã publish sẽ mở bán trong
 * 15 phút tới.
 */
@IntegrationTest
class OpeningSoonMetricTests {

	private static final Instant NOW = Instant.parse("2026-12-01T03:00:00Z");

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	MeterRegistry metrics;

	@Autowired
	OpeningSoonMetric metric;

	@BeforeEach
	void prepare() {
		cleanUp();
		clock.setInstant(NOW);
	}

	@AfterEach
	void cleanUp() {
		clock.reset();
		jdbc.update("delete from catalog.events where title = 'Sắp mở bán'");
	}

	@Test
	@DisplayName("AC-11: đếm sự kiện PUBLISHED mở bán trong 15 phút tới; ngoài khoảng hoặc đã ON_SALE thì không đếm")
	void countsOnlyPublishedEventsOpeningWithinTheWindow() {
		event("PUBLISHED", NOW.plus(Duration.ofMinutes(10)));
		event("PUBLISHED", NOW.plus(Duration.ofMinutes(15)));
		event("PUBLISHED", NOW.plus(Duration.ofMinutes(16)));
		event("PUBLISHED", NOW.minus(Duration.ofMinutes(1)));
		event("ON_SALE", NOW.plus(Duration.ofMinutes(5)));

		metric.refresh();

		assertThat(opening()).isEqualTo(2);
	}

	@Test
	@DisplayName("AC-11: hết sự kiện sắp mở thì về 0 (KEDA về mức thường)")
	void dropsBackToZeroWhenNothingIsOpeningSoon() {
		event("PUBLISHED", NOW.plus(Duration.ofMinutes(10)));
		metric.refresh();
		assertThat(opening()).isEqualTo(1);

		clock.setInstant(NOW.plus(Duration.ofMinutes(11)));
		metric.refresh();

		assertThat(opening()).isZero();
	}

	private double opening() {
		return metrics.get("tickethub.events.opening_soon").gauge().value();
	}

	private void event(String status, Instant saleStartsAt) {
		jdbc.update("""
				insert into catalog.events (id, organizer_id, venue_id, seat_map_id, title, status, starts_at, ends_at,
				    sale_starts_at, platform_fee_bps)
				select ?, ?, v.id, m.id, 'Sắp mở bán', ?, ?, ?, ?, 500
				from catalog.venues v join catalog.seat_maps m on m.venue_id = v.id
				limit 1
				""", UUID.randomUUID(), TestTokens.ORGANIZER_ID, status, Timestamp.from(saleStartsAt.plus(Duration.ofDays(30))),
				Timestamp.from(saleStartsAt.plus(Duration.ofDays(30)).plus(Duration.ofHours(3))),
				Timestamp.from(saleStartsAt));
	}

}
