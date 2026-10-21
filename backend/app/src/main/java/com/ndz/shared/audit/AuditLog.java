package com.ndz.shared.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một dòng nhật ký thao tác quản trị: ai, lúc nào, làm gì, trên đối tượng nào, trước / sau (overview.md §13.4). */
@Entity
@Table(schema = "shared", name = "audit_log")
public class AuditLog {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AuditAction action;

	@Column(nullable = false, updatable = false)
	private String targetType;

	@Column(nullable = false, updatable = false)
	private String targetId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private Map<String, Object> beforeState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private Map<String, Object> afterState;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	AuditLog(UUID id, UUID actorId, AuditAction action, String targetType, String targetId,
			Map<String, Object> beforeState, Map<String, Object> afterState, Instant createdAt) {
		this.id = id;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.beforeState = beforeState;
		this.afterState = afterState;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorId() {
		return actorId;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getTargetType() {
		return targetType;
	}

	public String getTargetId() {
		return targetId;
	}

	public Map<String, Object> getBeforeState() {
		return beforeState;
	}

	public Map<String, Object> getAfterState() {
		return afterState;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
