package com.ndz.catalog.constant;

/** Giới hạn file import Excel (docs/design/README.md §7). */
public final class ImportLimits {

	public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
	public static final int MAX_ROWS = 5_000;
	/** Số lỗi trả kèm trong response; đủ hơn nằm trong file lỗi. */
	public static final int MAX_ERRORS_IN_RESPONSE = 200;
	/** Tổng số ghế một lần import sơ đồ ghế được thêm, cùng trần với sơ đồ sinh từ lưới. */
	public static final int MAX_SEATS_PER_IMPORT = 50_000;

	private ImportLimits() {
	}

}
