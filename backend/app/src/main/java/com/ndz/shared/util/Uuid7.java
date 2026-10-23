package com.ndz.shared.util;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUIDv7 (RFC 9562): 48 bit đầu là mili-giây Unix nên sắp xếp được theo thời gian tạo, giúp index khoá chính ít phân
 * mảnh hơn UUIDv4. Mọi khoá chính do app sinh dùng hàm này (overview.md, "Khoá chính UUIDv7").
 */
public final class Uuid7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Uuid7() {
	}

	public static UUID next() {
		return next(Clock.systemUTC());
	}

	public static UUID next(Clock clock) {
		long millis = clock.millis();
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);

		long high = (millis << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
		long low = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
		for (int i = 3; i < 10; i++) {
			low |= (random[i] & 0xFFL) << (8 * (9 - i));
		}
		return new UUID(high, low);
	}

}
