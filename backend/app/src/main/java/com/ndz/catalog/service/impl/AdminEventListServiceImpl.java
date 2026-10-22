package com.ndz.catalog.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.request.AdminEventFilter;
import com.ndz.catalog.dto.response.AdminEventResponses.AdminEventPage;
import com.ndz.catalog.dto.response.AdminEventResponses.AdminEventSummary;
import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.Organizer;
import com.ndz.catalog.repository.EventRepository;
import com.ndz.catalog.repository.EventSpecifications;
import com.ndz.catalog.repository.OrganizerRepository;
import com.ndz.catalog.service.AdminEventListService;
import com.ndz.shared.export.CsvExporter;

@Service
class AdminEventListServiceImpl implements AdminEventListService {

	private static final int MAX_PAGE_SIZE = 100;

	private final EventRepository events;
	private final OrganizerRepository organizers;
	private final CsvExporter csv;

	AdminEventListServiceImpl(EventRepository events, OrganizerRepository organizers, CsvExporter csv) {
		this.events = events;
		this.organizers = organizers;
		this.csv = csv;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminEventPage list(AdminEventFilter filter, int page, int size) {
		int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		Page<Event> result = events.findAll(buildSpec(filter),
				PageRequest.of(Math.max(page, 0), limit, org.springframework.data.domain.Sort
						.by(org.springframework.data.domain.Sort.Direction.DESC, "startsAt")));
		Map<UUID, String> organizerNames = organizerNames(result.getContent());
		return new AdminEventPage(
				result.getContent().stream().map(event -> toSummary(event, organizerNames)).toList(),
				result.getTotalElements(), Math.max(page, 0), limit);
	}

	@Override
	@Transactional
	public String export(UUID adminId, AdminEventFilter filter) {
		List<Event> rows = events.findAll(buildSpec(filter));
		csv.requireWithinLimit(rows.size());
		Map<UUID, String> organizerNames = organizerNames(rows);
		Map<String, Object> filters = new HashMap<>();
		filters.put("status", filter.status());
		filters.put("organizerId", filter.organizerId());
		filters.put("from", filter.from());
		filters.put("to", filter.to());
		filters.put("q", filter.q());
		filters.values().removeIf(java.util.Objects::isNull);
		return csv.export(adminId, "admin_events", filters,
				List.of("Mã sự kiện", "Tên sự kiện", "Trạng thái", "Ban tổ chức", "Giờ diễn", "Giờ mở bán"),
				rows.stream().<List<?>>map(event -> List.of(event.getId(), event.getTitle(), event.getStatus(),
						organizerNames.getOrDefault(event.getOrganizerId(), ""), event.getStartsAt(),
						event.getSaleStartsAt())).toList());
	}

	private Map<UUID, String> organizerNames(List<Event> rows) {
		List<UUID> ids = rows.stream().map(Event::getOrganizerId).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> names = new HashMap<>();
		for (Organizer organizer : organizers.findAllById(ids)) {
			names.put(organizer.getId(), organizer.getDisplayName());
		}
		return names;
	}

	private static AdminEventSummary toSummary(Event event, Map<UUID, String> organizerNames) {
		return new AdminEventSummary(event.getId(), event.getTitle(), event.getStatus(), event.getOrganizerId(),
				organizerNames.getOrDefault(event.getOrganizerId(), ""), event.getStartsAt(),
				event.getSaleStartsAt());
	}

	private static Specification<Event> buildSpec(AdminEventFilter filter) {
		Specification<Event> spec = (root, query, builder) -> builder.conjunction();
		if (filter.status() != null) {
			spec = spec.and(EventSpecifications.hasStatus(filter.status()));
		}
		if (filter.organizerId() != null) {
			spec = spec.and(EventSpecifications.ownedBy(filter.organizerId()));
		}
		if (filter.from() != null) {
			spec = spec.and(EventSpecifications.startsAtFrom(filter.from()));
		}
		if (filter.to() != null) {
			spec = spec.and(EventSpecifications.startsAtTo(filter.to()));
		}
		if (filter.q() != null && !filter.q().isBlank()) {
			spec = spec.and(EventSpecifications.titleContains(filter.q().trim()));
		}
		return spec;
	}

}
