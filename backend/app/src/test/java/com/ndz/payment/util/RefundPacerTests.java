package com.ndz.payment.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Giới hạn tốc độ gọi Stripe khi hoàn hàng loạt (AC-6, BR-08: mặc định 10 lần / giây). */
class RefundPacerTests {

	@Test
	@DisplayName("AC-6: với giới hạn mặc định 10 / giây, 21 lượt cần ít nhất 2 giây (không vượt giới hạn)")
	void defaultRateIsNeverExceeded() throws InterruptedException {
		RefundPacer pacer = new RefundPacer(10);
		long start = System.nanoTime();
		for (int i = 0; i < 21; i++) {
			pacer.acquire();
		}
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(1950));
	}

}
