package com.ndz.catalog.service;

import java.util.List;
import java.util.Set;

import com.ndz.catalog.dto.response.ImportResponses.ImportContext;
import com.ndz.catalog.dto.response.ImportResponses.ImportOutcome;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.util.ExcelFiles.GuideLine;
import com.ndz.catalog.util.ExcelFiles.ImportRow;

/**
 * Một loại import Excel: khai báo cột, file mẫu và cách kiểm / ghi từng dòng. {@code run} dùng chung đường kiểm cho cả
 * dry-run và import thật; dry-run không ghi gì. Dòng lỗi không chặn dòng hợp lệ.
 */
public interface ImportHandler {

	ImportType type();

	List<String> columns();

	/** Cột phải giữ dạng chữ trong file mẫu (mã có số 0 đứng đầu). */
	Set<String> textColumns();

	List<List<String>> examples();

	List<GuideLine> guide();

	/** Kiểm điều kiện đầu vào (vd. sơ đồ đích) trước khi tạo bản ghi import; vi phạm thì ném BusinessException. */
	default void prepare(ImportContext context) {
	}

	ImportOutcome run(List<ImportRow> rows, ImportContext context);

}
