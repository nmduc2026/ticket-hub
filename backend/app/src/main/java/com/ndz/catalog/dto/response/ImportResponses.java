package com.ndz.catalog.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportStatus;

/** Kết quả import Excel (overview.md §11.2). */
public final class ImportResponses {

	private ImportResponses() {
	}

	/** Tham số một lần import: dry-run hay ghi thật, xử lý mã trùng, sơ đồ đích (chỉ với sơ đồ ghế). */
	public record ImportContext(boolean dryRun, DuplicateMode onDuplicate, UUID seatMapId) {
	}

	/** {@code created + updated + skipped} là số dòng hợp lệ. */
	public record ImportOutcome(int created, int updated, int skipped, List<RowError> errors) {

		public int validRows() {
			return created + updated + skipped;
		}
	}

	@Schema(description = "Một lỗi của file import: dòng Excel (dòng 1 là tiêu đề), tên cột, nội dung lỗi")
	public record RowError(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int row,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String column,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
	}

	public record ImportResult(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ImportStatus status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dryRun,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalRows,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int validRows,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int errorRows,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int created,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int updated,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skipped,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Tối đa 200 lỗi đầu tiên; đủ hơn trong file lỗi") List<RowError> errors,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "true khi có file lỗi để tải ở GET /admin/imports/{id}/errors") boolean hasErrorFile) {
	}

}
