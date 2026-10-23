package com.ndz.shared.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc {@code audit_log} (màn #31, overview.md §13.4); mỗi hàm độc lập để ghép bằng {@code and}. */
public final class AuditLogSpecifications {

	private AuditLogSpecifications() {
	}

	public static Specification<AuditLog> byActor(UUID actorId) {
		return (root, query, builder) -> builder.equal(root.get("actorId"), actorId);
	}

	public static Specification<AuditLog> byAction(AuditAction action) {
		return (root, query, builder) -> builder.equal(root.get("action"), action);
	}

	public static Specification<AuditLog> createdFrom(Instant from) {
		return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), from);
	}

	public static Specification<AuditLog> createdTo(Instant to) {
		return (root, query, builder) -> builder.lessThan(root.get("createdAt"), to);
	}

}
