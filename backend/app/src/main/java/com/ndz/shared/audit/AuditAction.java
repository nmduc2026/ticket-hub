package com.ndz.shared.audit;

/** Thao tác quản trị được ghi vào {@code audit_log} (overview.md §13.4). */
public enum AuditAction {

	ORGANIZER_APPROVED, ORGANIZER_REJECTED, USER_LOCKED, USER_UNLOCKED, REFUND_RETRIED

}
