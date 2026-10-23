package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.catalog.constant.ImportLimits;
import com.ndz.catalog.dto.response.ImportResponses.ImportResult;
import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.catalog.entity.ImportJob;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportStatus;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.repository.ImportJobRepository;
import com.ndz.catalog.service.AdminImportService;
import com.ndz.catalog.service.ImportHandler;
import com.ndz.catalog.dto.response.ImportResponses.ImportContext;
import com.ndz.catalog.dto.response.ImportResponses.ImportOutcome;
import com.ndz.catalog.util.ExcelFiles;
import com.ndz.catalog.util.ExcelFiles.ImportRow;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.storage.ObjectStorage;
import com.ndz.shared.util.Uuid7;

@Service
class AdminImportServiceImpl implements AdminImportService {

	private final Map<ImportType, ImportHandler> handlers = new EnumMap<>(ImportType.class);
	private final ImportJobRepository jobs;
	private final ObjectStorage storage;
	private final AuditLogService audit;
	private final Clock clock;
	private final TransactionTemplate tx;

	AdminImportServiceImpl(List<ImportHandler> handlers, ImportJobRepository jobs, ObjectStorage storage,
			AuditLogService audit, Clock clock, TransactionTemplate tx) {
		this.tx = tx;
		handlers.forEach(handler -> this.handlers.put(handler.type(), handler));
		this.jobs = jobs;
		this.storage = storage;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	public byte[] template(ImportType type) {
		ImportHandler handler = handlers.get(type);
		return ExcelFiles.template(handler.columns(), handler.textColumns(), handler.examples(), handler.guide());
	}

	@Override
	public ImportResult importFile(UUID adminId, ImportType type, byte[] file, boolean dryRun, DuplicateMode onDuplicate,
			UUID seatMapId) {
		if (file.length == 0) {
			throw new BusinessException(ErrorCode.EMPTY_FILE, "File trống");
		}
		if (file.length > ImportLimits.MAX_FILE_BYTES) {
			throw new BusinessException(ErrorCode.IMPORT_FILE_TOO_LARGE, "File quá 5 MB");
		}
		ImportHandler handler = handlers.get(type);
		ImportContext context = new ImportContext(dryRun, onDuplicate, seatMapId);
		handler.prepare(context);
		List<ImportRow> rows = ExcelFiles.read(file, handler.columns());

		UUID id = Uuid7.next(clock);
		String fileKey = "imports/" + id + "/source.xlsx";
		storage.put(fileKey, file, ExcelFiles.CONTENT_TYPE);
		ImportJob job = new ImportJob(id, type, fileKey, dryRun, adminId, clock.instant());

		ImportOutcome outcome;
		try {
			outcome = handler.run(rows, context);
		}
		catch (RuntimeException e) {
			job.finish(ImportStatus.FAILED, rows.size(), 0, 0, null);
			tx.executeWithoutResult(ignored -> jobs.save(job));
			throw e;
		}

		List<RowError> errors = outcome.errors().stream()
				.sorted(Comparator.comparingInt(RowError::row).thenComparing(RowError::column)).toList();
		int errorRows = (int) errors.stream().map(RowError::row).distinct().count();
		String errorFileKey = null;
		if (!errors.isEmpty()) {
			errorFileKey = "imports/" + id + "/errors.xlsx";
			storage.put(errorFileKey, ExcelFiles.errorFile(errors), ExcelFiles.CONTENT_TYPE);
		}
		ImportStatus status = dryRun ? (errors.isEmpty() ? ImportStatus.VALID : ImportStatus.INVALID)
				: ImportStatus.IMPORTED;
		job.finish(status, rows.size(), outcome.validRows(), errorRows, errorFileKey);
		Map<String, Object> summary = new HashMap<>();
		summary.put("type", type.name());
		summary.put("created", outcome.created());
		summary.put("updated", outcome.updated());
		summary.put("skipped", outcome.skipped());
		summary.put("errorRows", errorRows);
		// Lưu bản ghi import và audit cùng một transaction (audit_log bắt buộc có transaction).
		tx.executeWithoutResult(ignored -> {
			jobs.save(job);
			if (!dryRun) {
				audit.record(adminId, AuditAction.DATA_IMPORTED, "import_job", id, null, summary);
			}
		});
		return new ImportResult(id, status, dryRun, rows.size(), outcome.validRows(), errorRows, outcome.created(),
				outcome.updated(), outcome.skipped(), errors.stream().limit(ImportLimits.MAX_ERRORS_IN_RESPONSE).toList(),
				errorFileKey != null);
	}

	@Override
	public byte[] errorFile(UUID importId) {
		ImportJob job = jobs.findById(importId)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_JOB_NOT_FOUND, "Không tìm thấy lần import"));
		if (job.getErrorFileKey() == null) {
			throw new BusinessException(ErrorCode.IMPORT_JOB_NOT_FOUND, "Lần import này không có lỗi");
		}
		return storage.get(job.getErrorFileKey());
	}

}
