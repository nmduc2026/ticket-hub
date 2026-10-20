package com.ndz.backend.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class Uuid7Tests {

	@Test
	void hasVersionSevenAndRfcVariant() {
		UUID id = Uuid7.next();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void firstFortyEightBitsAreTheUnixMilliseconds() {
		Instant moment = Instant.parse("2026-10-20T12:00:00.123Z");
		UUID id = Uuid7.next(Clock.fixed(moment, ZoneOffset.UTC));

		assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(moment.toEpochMilli());
	}

	@Test
	void idsGeneratedLaterSortAfterEarlierOnesByTheirTimestampPart() {
		UUID earlier = Uuid7.next(Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
		UUID later = Uuid7.next(Clock.fixed(Instant.parse("2026-10-20T12:00:01Z"), ZoneOffset.UTC));

		assertThat(later.getMostSignificantBits() >>> 16).isGreaterThan(earlier.getMostSignificantBits() >>> 16);
	}

	@Test
	void idsInTheSameMillisecondDoNotCollide() {
		Clock fixed = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(Uuid7.next(fixed));
		}

		assertThat(ids).hasSize(10_000);
	}

}
