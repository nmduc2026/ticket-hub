package com.ndz.catalog.service;

import java.util.UUID;

import com.ndz.catalog.dto.response.ImportResponses.ImportResult;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;

/** Import Excel cho Admin: đơn vị hành chính, địa điểm, sơ đồ ghế (overview.md §6.1, §11.2). */
public interface AdminImportService {

	/** File mẫu {@code .xlsx} của loại import. */
	byte[] template(ImportType type);

	/**
	 * Kiểm file; khi {@code dryRun = false} thì ghi các dòng hợp lệ (dòng lỗi không chặn dòng hợp lệ). {@code seatMapId}
	 * chỉ dùng cho {@code SEAT_MAPS}.
	 */
	ImportResult importFile(UUID adminId, ImportType type, byte[] file, boolean dryRun, DuplicateMode onDuplicate,
			UUID seatMapId);

	/** File lỗi {@code .xlsx} của một lần import; {@code IMPORT_JOB_NOT_FOUND} nếu lần đó không có lỗi. */
	byte[] errorFile(UUID importId);

}
