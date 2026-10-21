package com.ndz.shared.util;

import java.util.regex.Pattern;

/** Số điện thoại Việt Nam: {@code 0} hoặc {@code +84} rồi 9 chữ số (hồ sơ organizer, hồ sơ cá nhân). */
public final class VietnamesePhone {

	private static final Pattern PHONE = Pattern.compile("(0|\\+84)\\d{9}");

	private VietnamesePhone() {
	}

	public static boolean isValid(String phone) {
		return phone != null && PHONE.matcher(phone).matches();
	}

}
