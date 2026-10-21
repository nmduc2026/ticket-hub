package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.response.OrganizerResponses.BulkApproveResult;
import com.ndz.catalog.dto.response.OrganizerResponses.OrganizerPage;
import com.ndz.catalog.dto.response.OrganizerResponses.ReviewResult;
import com.ndz.catalog.dto.response.OrganizerResponses.Skipped;
import com.ndz.catalog.entity.Organizer;
import com.ndz.catalog.entity.enums.OrganizerStatus;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.catalog.mapper.OrganizerMapper;
import com.ndz.catalog.repository.OrganizerRepository;
import com.ndz.catalog.service.OrganizerAdminService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

/**
 * Admin duyệt / từ chối hồ sơ organizer (overview.md §7.6). Mỗi lần chuyển khoá dòng organizer và phát event trong
 * cùng transaction; Account gán role khi nhận {@code OrganizerApproved}.
 */
@Service
public class OrganizerAdminServiceImpl implements OrganizerAdminService {

	private static final Logger log = LoggerFactory.getLogger(OrganizerAdminServiceImpl.class);

	private static final int MAX_PAGE_SIZE = 100;

	private final OrganizerRepository organizers;
	private final OrganizerMapper mapper;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	OrganizerAdminServiceImpl(OrganizerRepository organizers, OrganizerMapper mapper,
			ApplicationEventPublisher publisher, Clock clock) {
		this.organizers = organizers;
		this.mapper = mapper;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public OrganizerPage list(OrganizerStatus status, int page, int size) {
		int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int pageNumber = Math.max(page, 0);
		Page<Organizer> result = organizers.findApplications(status, PageRequest.of(pageNumber, pageSize));
		return new OrganizerPage(result.getContent().stream().map(mapper::toAdminItem).toList(),
				result.getTotalElements(), pageNumber, pageSize);
	}

	@Override
	@Transactional
	public ReviewResult approve(UUID adminId, UUID organizerId) {
		Organizer organizer = organizers.lockById(organizerId).orElseThrow(OrganizerAdminServiceImpl::notFound);
		approveLocked(adminId, organizer);
		return new ReviewResult(organizer.getStatus());
	}

	@Override
	@Transactional
	public ReviewResult reject(UUID adminId, UUID organizerId, String reason) {
		Organizer organizer = organizers.lockById(organizerId).orElseThrow(OrganizerAdminServiceImpl::notFound);
		String trimmed = reason.trim();
		organizer.reject(trimmed);
		publisher.publishEvent(new OrganizerRejected(Uuid7.next(clock), clock.instant(), organizerId,
				organizer.getDisplayName(), organizer.getContactEmail(), trimmed));
		log.info("Admin {} từ chối hồ sơ organizer {}", adminId, organizerId);
		return new ReviewResult(organizer.getStatus());
	}

	@Override
	@Transactional
	public BulkApproveResult bulkApprove(UUID adminId, List<UUID> organizerIds) {
		List<UUID> approved = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		for (UUID id : new LinkedHashSet<>(organizerIds)) {
			Organizer organizer = organizers.lockById(id).orElse(null);
			if (organizer == null) {
				skipped.add(new Skipped(id, ErrorCode.ORGANIZER_NOT_FOUND.name()));
			}
			else if (!organizer.isPending()) {
				skipped.add(new Skipped(id, ErrorCode.ORGANIZER_NOT_PENDING.name()));
			}
			else {
				approveLocked(adminId, organizer);
				approved.add(id);
			}
		}
		return new BulkApproveResult(approved, skipped);
	}

	private void approveLocked(UUID adminId, Organizer organizer) {
		Instant now = clock.instant();
		organizer.approve(now);
		publisher.publishEvent(new OrganizerApproved(Uuid7.next(clock), now, organizer.getId(),
				organizer.getDisplayName(), organizer.getContactEmail()));
		log.info("Admin {} duyệt organizer {}", adminId, organizer.getId());
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.ORGANIZER_NOT_FOUND, "Không tìm thấy hồ sơ organizer");
	}

}
