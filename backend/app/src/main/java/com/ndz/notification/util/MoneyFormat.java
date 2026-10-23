package com.ndz.notification.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Hiển thị tiền trong thư và thông báo (lớp hiển thị, BR-10: số nguyên đơn vị nhỏ nhất, VND không nhân 100). */
public final class MoneyFormat {

	private MoneyFormat() {
	}

	/** VND hiển thị 950.000 ₫; tiền tệ khác ghi kèm mã. */
	public static String format(long amountMinor, String currency) {
		String number = NumberFormat.getIntegerInstance(Locale.of("vi", "VN")).format(amountMinor);
		return "VND".equals(currency) ? number + " ₫" : number + " " + currency;
	}

}
