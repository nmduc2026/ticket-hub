package com.ndz.catalog.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

/**
 * Một mức phí nền tảng có hiệu lực từ {@code effectiveFrom} (BR-06, ADR 0023). Bản ghi chỉ thêm, không sửa: đổi phí là
 * thêm mức mới; sự kiện chốt mức đang hiệu lực lúc publish. Envers ghi lại người tạo mỗi mức.
 */
@Entity
@Table(schema = "catalog", name = "platform_fee_policies")
@AuditTable(schema = "catalog", value = "platform_fee_policies_aud")
public class PlatformFeePolicy {

	@Id
	private UUID id;

	@Audited
	@Column(name = "fee_bps", nullable = false, updatable = false)
	private int feeBps;

	@Audited
	@Column(name = "effective_from", nullable = false, updatable = false)
	private Instant effectiveFrom;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected PlatformFeePolicy() {
	}

	public PlatformFeePolicy(UUID id, int feeBps, Instant effectiveFrom, UUID createdBy) {
		this.id = id;
		this.feeBps = feeBps;
		this.effectiveFrom = effectiveFrom;
		this.createdBy = createdBy;
	}

	public UUID getId() {
		return id;
	}

	public int getFeeBps() {
		return feeBps;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
