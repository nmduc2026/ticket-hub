package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.request.EventFilter;
import com.ndz.catalog.dto.request.EventRequest;
import com.ndz.catalog.dto.request.TierRequests.AssignmentRequest;
import com.ndz.catalog.dto.request.TierRequests.AssignmentsRequest;
import com.ndz.catalog.dto.request.TierRequests.TierRequest;
import com.ndz.catalog.dto.response.EventResponses.Assignment;
import com.ndz.catalog.dto.response.EventResponses.Detail;
import com.ndz.catalog.dto.response.EventResponses.EventPage;
import com.ndz.catalog.dto.response.EventResponses.Tier;
import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.EventContent;
import com.ndz.catalog.entity.TicketTier;
import com.ndz.catalog.entity.TierSection;
import com.ndz.catalog.entity.enums.EventStatus;
import com.ndz.catalog.mapper.EventMapper;
import com.ndz.catalog.mapper.EventUpdatedFactory;
import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.repository.EventRepository;
import com.ndz.catalog.repository.EventSpecifications;
import com.ndz.catalog.repository.TicketTierRepository;
import com.ndz.catalog.repository.TierSectionRepository;
import com.ndz.catalog.service.OrganizerEventService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

/**
 * Mọi truy cập đi qua {@code findByIdAndOrganizerId}: sự kiện của người khác cho kết quả "không tìm thấy" như sự kiện
 * không tồn tại (chống IDOR). Quy tắc khoá sau publish (BR-14) nằm ở {@link Event}.
 */
@Service
public class OrganizerEventServiceImpl implements OrganizerEventService {

	private static final int MAX_PAGE_SIZE = 100;

	private final EventRepository events;
	private final TicketTierRepository tiers;
	private final TierSectionRepository tierSections;
	private final CatalogReadRepository catalog;
	private final EventMapper mapper;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	OrganizerEventServiceImpl(EventRepository events, TicketTierRepository tiers, TierSectionRepository tierSections,
			CatalogReadRepository catalog, EventMapper mapper, ApplicationEventPublisher publisher, Clock clock) {
		this.publisher = publisher;
		this.events = events;
		this.tiers = tiers;
		this.tierSections = tierSections;
		this.catalog = catalog;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Detail create(UUID organizerId, EventRequest request) {
		EventContent content = mapper.toContent(request);
		if (!catalog.organizerExists(organizerId)) {
			throw new BusinessException(ErrorCode.ORGANIZER_NOT_REGISTERED, "Tài khoản chưa đăng ký ban tổ chức");
		}
		validateSalesInfo(content);
		validateDoorsOpen(content);
		Event event = events.save(Event.draft(Uuid7.next(clock), organizerId, content, clock.instant()));
		return detail(event);
	}

	@Override
	@Transactional
	public Detail update(UUID organizerId, UUID eventId, EventRequest request) {
		EventContent content = mapper.toContent(request);
		Event event = ownedEvent(organizerId, eventId);
		UUID seatMapBefore = event.getSeatMapId();
		if (event.getStatus() == EventStatus.DRAFT) {
			validateSalesInfo(content);
		}
		validateDoorsOpen(content);
		event.update(content);
		if (event.getStatus() == EventStatus.DRAFT && !Objects.equals(seatMapBefore, event.getSeatMapId())) {
			// Khu của sơ đồ cũ không còn thuộc sự kiện: bỏ các gán hạng vé cũ.
			tierSections.deleteByEventId(eventId);
		}
		events.save(event);
		if (event.getStatus() != EventStatus.DRAFT) {
			// Sự kiện đã publish: thông tin hiển thị đổi thì các module khác cập nhật bản sao của họ.
			publisher.publishEvent(EventUpdatedFactory.of(event, clock));
		}
		return detail(event);
	}

	@Override
	@Transactional(readOnly = true)
	public Detail get(UUID organizerId, UUID eventId) {
		return detail(ownedEvent(organizerId, eventId));
	}

	@Override
	@Transactional(readOnly = true)
	public EventPage list(UUID organizerId, EventFilter filter, int page, int size) {
		Specification<Event> spec = EventSpecifications.ownedBy(organizerId);
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			spec = spec.and(EventSpecifications.hasStatusIn(filter.statuses()));
		}
		if (filter.venueId() != null) {
			spec = spec.and(EventSpecifications.atVenue(filter.venueId()));
		}
		if (filter.query() != null && !filter.query().isBlank()) {
			spec = spec.and(EventSpecifications.titleContains(filter.query().trim()));
		}
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
		Page<Event> found = events.findAll(spec, request);
		Map<UUID, String> venueNames = catalog.venueNames(found.getContent().stream().map(Event::getVenueId)
				.filter(Objects::nonNull).collect(Collectors.toSet()));
		return new EventPage(
				found.getContent().stream()
						.map(event -> mapper.toSummary(event, venueNames.get(event.getVenueId()))).toList(),
				found.getTotalElements(), found.getNumber(), found.getSize());
	}

	// ---- Hạng vé ----

	@Override
	@Transactional(readOnly = true)
	public List<Tier> tiers(UUID organizerId, UUID eventId) {
		ownedEvent(organizerId, eventId);
		return tiers.findByEventIdOrderById(eventId).stream().map(mapper::toTier).toList();
	}

	@Override
	@Transactional
	public Tier createTier(UUID organizerId, UUID eventId, TierRequest request) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		return mapper.toTier(
				tiers.save(new TicketTier(Uuid7.next(clock), eventId, request.name().trim(), request.priceMinor())));
	}

	@Override
	@Transactional
	public Tier updateTier(UUID organizerId, UUID eventId, UUID tierId, TierRequest request) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		TicketTier tier = tier(eventId, tierId);
		tier.change(request.name().trim(), request.priceMinor());
		return mapper.toTier(tiers.save(tier));
	}

	@Override
	@Transactional
	public void deleteTier(UUID organizerId, UUID eventId, UUID tierId) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		TicketTier tier = tier(eventId, tierId);
		tierSections.deleteByTierId(tierId);
		tiers.delete(tier);
	}

	// ---- Gán hạng vé cho khu ghế ----

	@Override
	@Transactional
	public List<Assignment> replaceAssignments(UUID organizerId, UUID eventId, AssignmentsRequest request) {
		List<AssignmentRequest> assignments = request.assignments();
		Event event = ownedEvent(organizerId, eventId);
		event.requireDraftForSalesSetup();
		if (event.getSeatMapId() == null) {
			throw new BusinessException(ErrorCode.SEAT_MAP_REQUIRED, "Chọn sơ đồ ghế trước khi gán hạng vé");
		}
		Set<UUID> seen = new HashSet<>();
		for (AssignmentRequest assignment : assignments) {
			if (!seen.add(assignment.sectionId())) {
				throw new BusinessException(ErrorCode.DUPLICATE_SECTION, "Một khu chỉ được gán cho một hạng vé");
			}
		}
		Set<UUID> sectionsOfMap = catalog.sectionIdsOfSeatMap(event.getSeatMapId());
		Set<UUID> tierIds = tiers.findByEventIdOrderById(eventId).stream().map(TicketTier::getId)
				.collect(Collectors.toSet());
		for (AssignmentRequest assignment : assignments) {
			if (!sectionsOfMap.contains(assignment.sectionId())) {
				throw new BusinessException(ErrorCode.SECTION_NOT_IN_SEAT_MAP, "Khu không thuộc sơ đồ ghế của sự kiện");
			}
			if (!tierIds.contains(assignment.tierId())) {
				throw new BusinessException(ErrorCode.TIER_NOT_FOUND, "Hạng vé không thuộc sự kiện này");
			}
		}
		tierSections.deleteByEventId(eventId);
		List<Assignment> saved = new ArrayList<>();
		for (AssignmentRequest assignment : assignments) {
			TierSection section = tierSections
					.save(new TierSection(eventId, assignment.sectionId(), assignment.tierId()));
			saved.add(mapper.toAssignment(section));
		}
		return saved;
	}

	// ---- helpers ----

	private Event ownedEvent(UUID organizerId, UUID eventId) {
		return events.findByIdAndOrganizerId(eventId, organizerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
	}

	private TicketTier tier(UUID eventId, UUID tierId) {
		return tiers.findByIdAndEventId(tierId, eventId).orElseThrow(
				() -> new BusinessException(ErrorCode.TIER_NOT_FOUND, "Không tìm thấy hạng vé"));
	}

	private Detail detail(Event event) {
		String venueName = event.getVenueId() == null ? null
				: catalog.venueNames(Set.of(event.getVenueId())).get(event.getVenueId());
		return mapper.toDetail(event, venueName, tiers.findByEventIdOrderById(event.getId()),
				tierSections.findByEventId(event.getId()));
	}

	/**
	 * Giờ mở cửa không được sau giờ diễn. Kiểm cả sau publish vì giờ mở cửa vẫn sửa được; khi đó {@code startsAt}
	 * trong nội dung phải bằng giờ diễn đã khoá (BR-14), nếu khác thì {@code update} từ chối bằng {@code EVENT_LOCKED}.
	 */
	private static void validateDoorsOpen(EventContent content) {
		if (content.doorsOpenAt() != null && content.startsAt() != null
				&& content.doorsOpenAt().isAfter(content.startsAt())) {
			throw new BusinessException(ErrorCode.DOORS_OPEN_AFTER_START, "Giờ mở cửa không được sau giờ diễn");
		}
	}

	/** Kiểm thông tin bán vé của nháp: địa điểm và sơ đồ hợp lệ, giờ diễn / mở bán hợp lý. */
	private void validateSalesInfo(EventContent content) {
		if (content.startsAt() != null && content.endsAt() != null && !content.startsAt().isBefore(content.endsAt())) {
			throw new BusinessException(ErrorCode.INVALID_EVENT_TIMES, "Giờ kết thúc phải sau giờ bắt đầu");
		}
		if (content.saleStartsAt() != null && content.startsAt() != null
				&& content.saleStartsAt().isAfter(content.startsAt())) {
			throw new BusinessException(ErrorCode.INVALID_EVENT_TIMES, "Giờ mở bán không được sau giờ bắt đầu sự kiện");
		}
		if (content.venueId() != null && !catalog.venueExists(content.venueId())) {
			throw new BusinessException(ErrorCode.VENUE_NOT_FOUND, "Không tìm thấy địa điểm");
		}
		if (content.seatMapId() != null) {
			if (content.venueId() == null) {
				throw new BusinessException(ErrorCode.VENUE_REQUIRED, "Chọn địa điểm trước khi chọn sơ đồ ghế");
			}
			if (!catalog.seatMapBelongsToVenue(content.seatMapId(), content.venueId())) {
				throw new BusinessException(ErrorCode.SEAT_MAP_NOT_IN_VENUE, "Sơ đồ ghế không thuộc địa điểm đã chọn");
			}
		}
	}

}
