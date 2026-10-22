package com.ndz.account.service.impl;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.dto.request.AdminAuditLogFilter;
import com.ndz.account.dto.response.AdminAuditLogResponses.AdminAuditLogPage;
import com.ndz.account.dto.response.AdminAuditLogResponses.AdminAuditLogRow;
import com.ndz.account.entity.UserProfile;
import com.ndz.account.repository.UserProfileRepository;
import com.ndz.account.service.AdminAuditLogService;
import com.ndz.shared.audit.AuditLog;
import com.ndz.shared.audit.AuditLogRepository;
import com.ndz.shared.audit.AuditLogSpecifications;
import com.ndz.shared.export.CsvExporter;

@Service
class AdminAuditLogServiceImpl implements AdminAuditLogService {

	private static final int MAX_PAGE_SIZE = 100;

	private final AuditLogRepository logs;
	private final UserProfileRepository profiles;
	private final CsvExporter csv;

	AdminAuditLogServiceImpl(AuditLogRepository logs, UserProfileRepository profiles, CsvExporter csv) {
		this.logs = logs;
		this.profiles = profiles;
		this.csv = csv;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminAuditLogPage list(AdminAuditLogFilter filter, int page, int size) {
		int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		Page<AuditLog> result = logs.findAll(buildSpec(filter),
				PageRequest.of(Math.max(page, 0), limit, Sort.by(Sort.Direction.DESC, "createdAt")));
		Map<UUID, String> actorNames = actorNames(result.getContent());
		return new AdminAuditLogPage(result.getContent().stream().map(log -> toRow(log, actorNames)).toList(),
				result.getTotalElements(), Math.max(page, 0), limit);
	}

	@Override
	@Transactional
	public String export(UUID adminId, AdminAuditLogFilter filter) {
		List<AuditLog> rows = logs.findAll(buildSpec(filter), Sort.by(Sort.Direction.DESC, "createdAt"));
		csv.requireWithinLimit(rows.size());
		Map<UUID, String> actorNames = actorNames(rows);
		Map<String, Object> filters = new HashMap<>();
		filters.put("actor", filter.actor());
		filters.put("action", filter.action());
		filters.put("from", filter.from());
		filters.put("to", filter.to());
		filters.values().removeIf(java.util.Objects::isNull);
		return csv.export(adminId, "admin_audit_logs", filters,
				List.of("Người thao tác", "Hành động", "Đối tượng", "Mã đối tượng", "Lúc"),
				rows.stream().<List<?>>map(log -> List.of(actorNames.getOrDefault(log.getActorId(), ""),
						log.getAction(), log.getTargetType(), log.getTargetId(),
						log.getCreatedAt().atOffset(ZoneOffset.UTC))).toList());
	}

	private Map<UUID, String> actorNames(List<AuditLog> rows) {
		List<UUID> ids = rows.stream().map(AuditLog::getActorId).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> names = new HashMap<>();
		for (UserProfile profile : profiles.findAllById(ids)) {
			names.put(profile.getUserId(), profile.getFullName());
		}
		return names;
	}

	private static AdminAuditLogRow toRow(AuditLog log, Map<UUID, String> actorNames) {
		return new AdminAuditLogRow(log.getId(), log.getActorId(), actorNames.getOrDefault(log.getActorId(), ""),
				log.getAction(), log.getTargetType(), log.getTargetId(), log.getBeforeState(), log.getAfterState(),
				log.getCreatedAt());
	}

	private static Specification<AuditLog> buildSpec(AdminAuditLogFilter filter) {
		Specification<AuditLog> spec = (root, query, builder) -> builder.conjunction();
		if (filter.actor() != null) {
			spec = spec.and(AuditLogSpecifications.byActor(filter.actor()));
		}
		if (filter.action() != null) {
			spec = spec.and(AuditLogSpecifications.byAction(filter.action()));
		}
		if (filter.from() != null) {
			spec = spec.and(AuditLogSpecifications.createdFrom(filter.from()));
		}
		if (filter.to() != null) {
			spec = spec.and(AuditLogSpecifications.createdTo(filter.to()));
		}
		return spec;
	}

}
