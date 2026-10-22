package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.catalog.dto.response.SuspensionResponses.BulkItem;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.dto.response.SuspensionResponses.EventStatusResult;
import com.ndz.catalog.dto.response.SuspensionResponses.OrganizerSuspension;
import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.Organizer;
import com.ndz.catalog.entity.enums.EventStatus;
import com.ndz.catalog.entity.enums.OrganizerStatus;
import com.ndz.catalog.event.EventCancelled;
import com.ndz.catalog.event.EventResumed;
import com.ndz.catalog.event.EventSuspended;
import com.ndz.catalog.event.OrganizerPayoutStatusChanged;
import com.ndz.catalog.repository.CatalogProcessedEventRepository;
import com.ndz.catalog.repository.EventRepository;
import com.ndz.catalog.repository.OrganizerRepository;
import com.ndz.catalog.service.EventSuspensionService;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
public class EventSuspensionServiceImpl implements EventSuspensionService {

	static final String PAYOUT_CONSUMER = "catalog.organizer-payout-status";

	private static final Logger log = LoggerFactory.getLogger(EventSuspensionServiceImpl.class);

	/** Trạng thái còn đang bán / chờ mở bán: bị tạm dừng theo organizer (BR-13). */
	private static final EnumSet<EventStatus> SELLING = EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE);

	private final EventRepository events;
	private final OrganizerRepository organizers;
	private final CatalogProcessedEventRepository processed;
	private final AuditLogService audit;
	private final ApplicationEventPublisher publisher;
	private final TransactionTemplate transaction;
	private final Clock clock;

	EventSuspensionServiceImpl(EventRepository events, OrganizerRepository organizers,
			CatalogProcessedEventRepository processed, AuditLogService audit,
			ApplicationEventPublisher publisher, TransactionTemplate transaction, Clock clock) {
		this.events = events;
		this.organizers = organizers;
		this.processed = processed;
		this.audit = audit;
		this.publisher = publisher;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	@Transactional
	public EventStatusResult suspendEvent(UUID adminId, UUID eventId, String reason) {
		Event event = event(eventId);
		EventStatus before = event.getStatus();
		suspend(event, reason, false);
		audit.record(adminId, AuditAction.EVENT_SUSPENDED, "event", eventId, Map.of("status", before.name()),
				Map.of("status", EventStatus.SUSPENDED.name(), "reason", reason));
		return new EventStatusResult(eventId, event.getStatus().name());
	}

	@Override
	@Transactional
	public EventStatusResult cancelEvent(UUID adminId, UUID eventId, String reason) {
		Event event = event(eventId);
		EventStatus before = event.getStatus();
		event.cancel();
		events.save(event);
		publisher.publishEvent(new EventCancelled(Uuid7.next(clock), clock.instant(), eventId, event.getOrganizerId(),
				event.getTitle(), reason));
		audit.record(adminId, AuditAction.EVENT_CANCELLED, "event", eventId, Map.of("status", before.name()),
				Map.of("status", EventStatus.CANCELLED.name(), "reason", reason));
		log.warn("Admin {} huỷ sự kiện {}: {}", adminId, eventId, reason);
		return new EventStatusResult(eventId, event.getStatus().name());
	}

	@Override
	public BulkResult bulkSuspend(UUID adminId, List<UUID> eventIds, String reason) {
		List<BulkItem> results = new ArrayList<>();
		for (UUID eventId : eventIds.stream().distinct().toList()) {
			try {
				// Mỗi sự kiện một transaction: một sự kiện lỗi không hoàn tác các sự kiện đã tạm dừng.
				transaction.executeWithoutResult(status -> suspendEvent(adminId, eventId, reason));
				results.add(new BulkItem(eventId, true, null));
			}
			catch (BusinessException e) {
				results.add(new BulkItem(eventId, false, e.code().name()));
			}
		}
		return new BulkResult(results);
	}

	@Override
	@Transactional
	public EventStatusResult resumeEvent(UUID adminId, UUID eventId) {
		Event event = event(eventId);
		// BR-13: chỉ mở bán lại khi organizer đang hoạt động và nhận được tiền.
		Organizer organizer = organizers.findById(event.getOrganizerId()).orElseThrow();
		if (organizer.getStatus() != OrganizerStatus.APPROVED) {
			throw new BusinessException(ErrorCode.ORGANIZER_SUSPENDED, "Ban tổ chức đang bị đình chỉ");
		}
		if (!organizer.isPayoutReady()) {
			throw new BusinessException(ErrorCode.ORGANIZER_PAYOUT_NOT_READY,
					"Ban tổ chức chưa kết nối xong Stripe để nhận tiền");
		}
		Instant now = clock.instant();
		event.resume(now);
		events.save(event);
		publisher.publishEvent(new EventResumed(Uuid7.next(clock), now, eventId, event.getStatus().name()));
		audit.record(adminId, AuditAction.EVENT_RESUMED, "event", eventId,
				Map.of("status", EventStatus.SUSPENDED.name()), Map.of("status", event.getStatus().name()));
		log.info("Admin {} mở bán lại sự kiện {} → {}", adminId, eventId, event.getStatus());
		return new EventStatusResult(eventId, event.getStatus().name());
	}

	@Override
	@Transactional
	public OrganizerSuspension suspendOrganizer(UUID adminId, UUID organizerId, String reason) {
		Organizer organizer = lockedOrganizer(organizerId);
		organizer.suspend(reason);
		organizers.save(organizer);
		List<Event> selling = events.findByOrganizerIdAndStatusIn(organizerId, SELLING);
		selling.forEach(event -> suspend(event, "Ban tổ chức bị đình chỉ: " + reason, true));
		audit.record(adminId, AuditAction.ORGANIZER_SUSPENDED, "organizer", organizerId,
				Map.of("status", OrganizerStatus.APPROVED.name()),
				Map.of("status", OrganizerStatus.SUSPENDED.name(), "reason", reason, "suspendedEvents", selling.size()));
		log.warn("Admin {} đình chỉ organizer {}, tạm dừng {} sự kiện", adminId, organizerId, selling.size());
		return new OrganizerSuspension(organizerId, organizer.getStatus().name(), selling.size());
	}

	@Override
	@Transactional
	public void onPayoutStatusChanged(OrganizerPayoutStatusChanged event) {
		if (!processed.markProcessed(PAYOUT_CONSUMER, event.messageId())) {
			log.info("Bỏ qua OrganizerPayoutStatusChanged đã xử lý {}", event.messageId());
			return;
		}
		Organizer organizer = lockedOrganizer(event.organizerId());
		organizer.changePayoutReady(event.payoutReady());
		organizers.save(organizer);
		if (event.payoutReady()) {
			log.info("Organizer {} đã nhận được tiền (charges_enabled)", event.organizerId());
			return;
		}
		List<Event> selling = events.findByOrganizerIdAndStatusIn(event.organizerId(), SELLING);
		selling.forEach(sale -> suspend(sale, "Tài khoản Stripe của ban tổ chức bị hạn chế", true));
		log.warn("Organizer {} mất charges_enabled: tạm dừng {} sự kiện", event.organizerId(), selling.size());
	}

	@Override
	@Transactional
	public OrganizerSuspension reinstateOrganizer(UUID adminId, UUID organizerId, String reason) {
		Organizer organizer = lockedOrganizer(organizerId);
		organizer.reinstate();
		organizers.save(organizer);
		audit.record(adminId, AuditAction.ORGANIZER_REINSTATED, "organizer", organizerId,
				Map.of("status", OrganizerStatus.SUSPENDED.name()),
				Map.of("status", OrganizerStatus.APPROVED.name(), "reason", reason));
		log.info("Admin {} khôi phục organizer {}", adminId, organizerId);
		return new OrganizerSuspension(organizerId, organizer.getStatus().name(), 0);
	}

	private void suspend(Event event, String reason, boolean automatic) {
		event.suspend(reason);
		events.save(event);
		publisher.publishEvent(new EventSuspended(Uuid7.next(clock), clock.instant(), event.getId(),
				event.getOrganizerId(), event.getTitle(), reason, automatic));
	}

	private Event event(UUID eventId) {
		return events.findById(eventId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
	}

	private Organizer lockedOrganizer(UUID organizerId) {
		return organizers.lockById(organizerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZER_NOT_FOUND, "Không tìm thấy ban tổ chức"));
	}

}
