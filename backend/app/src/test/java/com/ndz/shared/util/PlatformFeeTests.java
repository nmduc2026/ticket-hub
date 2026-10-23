package com.ndz.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BR-06, BR-10, AC-8: phí nền tảng làm tròn xuống, tính bằng số nguyên. */
class PlatformFeeTests {

	@Test
	@DisplayName("AC-8: 1.000.000 VND × 500 bps = 50.000 (VND không nhân 100)")
	void roundNumberOfVnd() {
		assertThat(PlatformFee.of(1_000_000, 500)).isEqualTo(50_000);
	}

	@Test
	@DisplayName("AC-8: 333.333 VND × 500 bps = 16.666 (làm tròn xuống, không lên 16.667)")
	void roundsDown() {
		assertThat(PlatformFee.of(333_333, 500)).isEqualTo(16_666);
	}

	@Test
	void zeroFeeOrZeroAmountIsZero() {
		assertThat(PlatformFee.of(1_000_000, 0)).isZero();
		assertThat(PlatformFee.of(0, 500)).isZero();
	}

	@Test
	void fullFeeIsTheWholeAmount() {
		assertThat(PlatformFee.of(777, 10_000)).isEqualTo(777);
	}

	@Test
	void amountsBelowOneBasisPointOfFeeRoundToZero() {
		assertThat(PlatformFee.of(19, 500)).isZero(); // 0,95 -> 0
		assertThat(PlatformFee.of(20, 500)).isEqualTo(1);
	}

	@Test
	void rejectsNegativeAmountAndOutOfRangeFee() {
		assertThatThrownBy(() -> PlatformFee.of(-1, 500)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlatformFee.of(100, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlatformFee.of(100, 10_001)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void overflowIsReportedInsteadOfWrappingAround() {
		assertThatThrownBy(() -> PlatformFee.of(Long.MAX_VALUE, 500)).isInstanceOf(ArithmeticException.class);
	}

}
