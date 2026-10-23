package com.ndz.shared.util;

/** Phí nền tảng (BR-06), tính ở một chỗ duy nhất. Tiền là số nguyên đơn vị nhỏ nhất, không dùng số thực (BR-10). */
public final class PlatformFee {

	private static final long BPS_DENOMINATOR = 10_000;

	private PlatformFee() {
	}

	/** {@code floor(amountMinor * feeBps / 10000)}: làm tròn xuống, VND không nhân 100. */
	public static long of(long amountMinor, int feeBps) {
		if (amountMinor < 0 || feeBps < 0 || feeBps > BPS_DENOMINATOR) {
			throw new IllegalArgumentException("Số tiền hoặc mức phí không hợp lệ: " + amountMinor + ", " + feeBps);
		}
		return Math.multiplyExact(amountMinor, (long) feeBps) / BPS_DENOMINATOR;
	}

}
