package com.ndz.backend.catalog.internal.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.EventContent;
import com.ndz.backend.catalog.internal.domain.EventStatus;
import com.ndz.backend.catalog.internal.domain.TicketTier;
import com.ndz.backend.catalog.internal.domain.TierSection;
import com.ndz.backend.catalog.internal.persistence.CatalogReadRepository;
import com.ndz.backend.catalog.internal.persistence.EventRepository;
import com.ndz.backend.catalog.internal.persistence.EventSpecifications;
import com.ndz.backend.catalog.internal.persistence.TicketTierRepository;
import com.ndz.backend.catalog.internal.persistence.TierSectionRepository;
import com.ndz.backend.shared.BusinessException;
import com.ndz.backend.shared.Uuid7;

/**
 * Use case của organizer trên sự kiện của chính họ (overview.md §11.2, §13.3). Mọi truy cập đi qua
 * {@code findByIdAndOrganizerId}: sự kiện của người khác cho kết quả "không tìm thấy" như sự kiện không tồn tại
 * (chống IDOR). Quy tắc khoá sau publish (BR-14) nằm ở {@link Event}.
 */
@Service
public class OrganizerEventService {

	private static final int MAX_PAGE_SIZE = 100;

	/** Điều kiện lọc danh sách; thiếu thì không lọc theo điều kiện đó. */
	public record EventFilter(Set<EventStatus> statuses, UUID venueId, String query) {
	}

	/** Một dòng danh sách kèm tên địa điểm. */
	public record EventRow(Event event, String venueName) {
	}

	/** Toàn bộ thông tin của một sự kiện để organizer chỉnh sửa. */
	public record EventDetail(Event event, String venueName, List<TicketTier> tiers, List<TierSection> assignments) {
	}

	/** Một dòng gán khu cho hạng vé. */
	public record Assignment(UUID sectionId, UUID tierId) {
	}

	private final EventRepository events;
	private final TicketTierRepository tiers;
	private final TierSectionRepository tierSections;
	private final CatalogReadRepository catalog;
	private final Clock clock;

	OrganizerEventService(EventRepository events, TicketTierRepository tiers, TierSectionRepository tierSections,
			CatalogReadRepository catalog, Clock clock) {
		this.events = events;
		this.tiers = tiers;
		this.tierSections = tierSections;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Transactional
	public EventDetail create(UUID organizerId, EventContent content) {
		if (!catalog.organizerExists(organizerId)) {
			throw new BusinessException("ORGANIZER_NOT_REGISTERED", "Tài khoản chưa đăng ký ban tổ chức",
					HttpStatus.FORBIDDEN);
		}
		validateSalesInfo(content);
		Event event = events.save(Event.draft(Uuid7.next(clock), organizerId, content, clock.instant()));
		return detail(event);
	}

	@Transactional
	public EventDetail update(UUID organizerId, UUID eventId, EventContent content) {
		Event event = ownedEvent(organizerId, eventId);
		UUID seatMapBefore = event.getSeatMapId();
		if (event.getStatus() == EventStatus.DRAFT) {
			validateSalesInfo(content);
		}
		event.update(content);
		if (event.getStatus() == EventStatus.DRAFT && !java.util.Objects.equals(seatMapBefore, event.getSeatMapId())) {
			// Khu của sơ đồ cũ không còn thuộc sự kiện: bỏ các gán hạng vé cũ.
			tierSections.deleteByEventId(eventId);
		}
		return detail(events.save(event));
	}

	@Transactional(readOnly = true)
	public EventDetail get(UUID organizerId, UUID eventId) {
		return detail(ownedEvent(organizerId, eventId));
	}

	@Transactional(readOnly = true)
	public Page<EventRow> list(UUID organizerId, EventFilter filter, int page, int size) {
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
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return found.map(event -> new EventRow(event, venueNames.get(event.getVenueId())));
	}

	// ---- Hạng vé ----

	@Transactional(readOnly = true)
	public List<TicketTier> tiers(UUID organizerId, UUID eventId) {
		ownedEvent(organizerId, eventId);
		return tiers.findByEventIdOrderById(eventId);
	}

	@Transactional
	public TicketTier createTier(UUID organizerId, UUID eventId, String name, long priceMinor) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		return tiers.save(new TicketTier(Uuid7.next(clock), eventId, name.trim(), priceMinor));
	}

	@Transactional
	public TicketTier updateTier(UUID organizerId, UUID eventId, UUID tierId, String name, long priceMinor) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		TicketTier tier = tier(eventId, tierId);
		tier.change(name.trim(), priceMinor);
		return tiers.save(tier);
	}

	@Transactional
	public void deleteTier(UUID organizerId, UUID eventId, UUID tierId) {
		ownedEvent(organizerId, eventId).requireDraftForSalesSetup();
		TicketTier tier = tier(eventId, tierId);
		tierSections.deleteByTierId(tierId);
		tiers.delete(tier);
	}

	// ---- Gán hạng vé cho khu ghế ----

	/** Thay toàn bộ cách gán: danh sách gửi lên là trạng thái mới; khu không có trong danh sách thì không bán. */
	@Transactional
	public List<TierSection> replaceAssignments(UUID organizerId, UUID eventId, List<Assignment> assignments) {
		Event event = ownedEvent(organizerId, eventId);
		event.requireDraftForSalesSetup();
		if (event.getSeatMapId() == null) {
			throw new BusinessException("SEAT_MAP_REQUIRED", "Chọn sơ đồ ghế trước khi gán hạng vé");
		}
		Set<UUID> seen = new HashSet<>();
		for (Assignment assignment : assignments) {
			if (!seen.add(assignment.sectionId())) {
				throw new BusinessException("DUPLICATE_SECTION", "Một khu chỉ được gán cho một hạng vé");
			}
		}
		Set<UUID> sectionsOfMap = catalog.sectionIdsOfSeatMap(event.getSeatMapId());
		Set<UUID> tierIds = tiers.findByEventIdOrderById(eventId).stream().map(TicketTier::getId)
				.collect(Collectors.toSet());
		for (Assignment assignment : assignments) {
			if (!sectionsOfMap.contains(assignment.sectionId())) {
				throw new BusinessException("SECTION_NOT_IN_SEAT_MAP", "Khu không thuộc sơ đồ ghế của sự kiện");
			}
			if (!tierIds.contains(assignment.tierId())) {
				throw new BusinessException("TIER_NOT_FOUND", "Hạng vé không thuộc sự kiện này", HttpStatus.NOT_FOUND);
			}
		}
		tierSections.deleteByEventId(eventId);
		List<TierSection> saved = new ArrayList<>();
		for (Assignment assignment : assignments) {
			saved.add(tierSections.save(new TierSection(eventId, assignment.sectionId(), assignment.tierId())));
		}
		return saved;
	}

	// ---- helpers ----

	private Event ownedEvent(UUID organizerId, UUID eventId) {
		return events.findByIdAndOrganizerId(eventId, organizerId)
				.orElseThrow(() -> new BusinessException("EVENT_NOT_FOUND", "Không tìm thấy sự kiện",
						HttpStatus.NOT_FOUND));
	}

	private TicketTier tier(UUID eventId, UUID tierId) {
		return tiers.findByIdAndEventId(tierId, eventId).orElseThrow(
				() -> new BusinessException("TIER_NOT_FOUND", "Không tìm thấy hạng vé", HttpStatus.NOT_FOUND));
	}

	private EventDetail detail(Event event) {
		String venueName = event.getVenueId() == null ? null
				: catalog.venueNames(Set.of(event.getVenueId())).get(event.getVenueId());
		return new EventDetail(event, venueName, tiers.findByEventIdOrderById(event.getId()),
				tierSections.findByEventId(event.getId()));
	}

	/** Kiểm thông tin bán vé của nháp: địa điểm và sơ đồ hợp lệ, giờ diễn / mở bán hợp lý. */
	private void validateSalesInfo(EventContent content) {
		if (content.startsAt() != null && content.endsAt() != null && !content.startsAt().isBefore(content.endsAt())) {
			throw new BusinessException("INVALID_EVENT_TIMES", "Giờ kết thúc phải sau giờ bắt đầu");
		}
		if (content.saleStartsAt() != null && content.startsAt() != null
				&& content.saleStartsAt().isAfter(content.startsAt())) {
			throw new BusinessException("INVALID_EVENT_TIMES", "Giờ mở bán không được sau giờ bắt đầu sự kiện");
		}
		if (content.venueId() != null && !catalog.venueExists(content.venueId())) {
			throw new BusinessException("VENUE_NOT_FOUND", "Không tìm thấy địa điểm", HttpStatus.NOT_FOUND);
		}
		if (content.seatMapId() != null) {
			if (content.venueId() == null) {
				throw new BusinessException("VENUE_REQUIRED", "Chọn địa điểm trước khi chọn sơ đồ ghế");
			}
			if (!catalog.seatMapBelongsToVenue(content.seatMapId(), content.venueId())) {
				throw new BusinessException("SEAT_MAP_NOT_IN_VENUE", "Sơ đồ ghế không thuộc địa điểm đã chọn");
			}
		}
	}

}
