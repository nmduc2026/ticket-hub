package com.ndz.account.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.audit.AuditAction;

/** Bộ lọc nhật ký thao tác quản trị (màn #31, overview.md §13.4). */
public record AdminAuditLogFilter(UUID actor, AuditAction action, Instant from, Instant to) {
}
