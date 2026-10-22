package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.util.ExcelFiles.GuideLine;
import com.ndz.catalog.util.ExcelFiles.ImportRow;

/**
 * Một loại import Excel: khai báo cột, file mẫu và cách kiểm / ghi từng dòng. {@code run} dùng chung đường kiểm cho cả
 * dry-run và import thật; dry-run không ghi gì. Dòng lỗi không chặn dòng hợp lệ.
 */
interface ImportHandler {

	ImportType type();

	List<String> columns();

	/** Cột phải giữ dạng chữ trong file mẫu (mã có số 0 đứng đầu). */
	Set<String> textColumns();

	List<List<String>> examples();

	List<GuideLine> guide();

	/** Kiểm điều kiện đầu vào (vd. sơ đồ đích) trước khi tạo bản ghi import; vi phạm thì ném BusinessException. */
	default void prepare(Context context) {
	}

	Outcome run(List<ImportRow> rows, Context context);

	record Context(boolean dryRun, DuplicateMode onDuplicate, UUID seatMapId) {
	}

	/** {@code created + updated + skipped} là số dòng hợp lệ. */
	record Outcome(int created, int updated, int skipped, List<RowError> errors) {

		int validRows() {
			return created + updated + skipped;
		}
	}

}
