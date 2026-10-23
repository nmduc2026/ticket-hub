package com.ndz.catalog.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ndz.catalog.entity.enums.ImportStatus;
import com.ndz.catalog.entity.enums.ImportType;

/** Một lần import Excel (overview.md §6.1); file gốc và file lỗi nằm ở object storage, tham chiếu qua key. */
@Entity
@Table(schema = "catalog", name = "import_jobs")
public class ImportJob {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ImportType type;

	@Column(name = "file_key", nullable = false)
	private String fileKey;

	@Column(name = "dry_run", nullable = false)
	private boolean dryRun;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ImportStatus status;

	@Column(name = "total_rows", nullable = false)
	private int totalRows;

	@Column(name = "valid_rows", nullable = false)
	private int validRows;

	@Column(name = "error_rows", nullable = false)
	private int errorRows;

	@Column(name = "error_file_key")
	private String errorFileKey;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ImportJob() {
	}

	public ImportJob(UUID id, ImportType type, String fileKey, boolean dryRun, UUID createdBy, Instant createdAt) {
		this.id = id;
		this.type = type;
		this.fileKey = fileKey;
		this.dryRun = dryRun;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.status = ImportStatus.VALIDATING;
	}

	public void finish(ImportStatus status, int totalRows, int validRows, int errorRows, String errorFileKey) {
		this.status = status;
		this.totalRows = totalRows;
		this.validRows = validRows;
		this.errorRows = errorRows;
		this.errorFileKey = errorFileKey;
	}

	public UUID getId() {
		return id;
	}

	public ImportType getType() {
		return type;
	}

	public ImportStatus getStatus() {
		return status;
	}

	public String getErrorFileKey() {
		return errorFileKey;
	}

}
