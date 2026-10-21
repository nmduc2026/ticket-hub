package com.ndz.backend.catalog.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.catalog.events.EventEnded;
import com.ndz.backend.catalog.events.EventOnSale;
import com.ndz.backend.catalog.events.EventPublished;
import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventDetail;
import com.ndz.backend.catalog.internal.domain.CatalogViews.OrganizerInfo;
import com.ndz.backend.catalog.internal.domain.CatalogViews.VenueInfo;
import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.EventStatus;
import com.ndz.backend.catalog.internal.persistence.CatalogReadRepository;
import com.ndz.backend.catalog.internal.persistence.EventRepository;
import com.ndz.backend.catalog.internal.persistence.TicketTierRepository;
import com.ndz.backend.catalog.internal.persistence.TierSectionRepository;
import com.ndz.backend.shared.exception.BusinessException;
import com.ndz.backend.shared.exception.ErrorCode;
import com.ndz.backend.shared.util.Uuid7;

/**
 * Vòng đời sự kiện (overview.md §7.7, §7.9): publish, chuyển sang đang bán / kết thúc theo giờ, nhận tin tồn kho ghế
 * đã sẵn sàng. Mọi chuyển trạng thái phát một event trong cùng transaction với việc lưu (Modulith ghi vào
 * {@code event_publication}).
 */
@Service
public class EventLifecycleService {

	private static final Logger log = LoggerFactory.getLogger(EventLifecycleService.class);

	/** Trạng thái bán / chờ bán chưa kết thúc: qua giờ kết thúc thì chuyển ENDED. */
	private static final EnumSet<EventStatus> ENDABLE = EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE,
			EventStatus.SUSPENDED);

	private final EventRepository events;
	private final TicketTierRepository tiers;
	private final TierSectionRepository tierSections;
	private final CatalogReadRepository catalog;
	private final OrganizerEventService eventService;
	private final CatalogProperties properties;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	EventLifecycleService(EventRepository events, TicketTierRepository tiers, TierSectionRepository tierSections,
			CatalogReadRepository catalog, OrganizerEventService eventService, CatalogProperties properties,
			ApplicationEventPublisher publisher, Clock clock) {
		this.events = events;
		this.tiers = tiers;
		this.tierSections = tierSections;
		this.catalog = catalog;
		this.eventService = eventService;
		this.properties = properties;
		this.publisher = publisher;
		this.clock = clock;
	}

	/**
	 * Organizer publish: kiểm BR-05 (đã duyệt và nhận được tiền) và đủ thông tin, chốt mức phí (BR-06), khoá sơ đồ ghế
	 * và thông tin bán vé (BR-14), rồi phát {@code EventPublished}.
	 */
	@Transactional
	public EventDetail publish(UUID organizerId, UUID eventId) {
		Event event = events.findByIdAndOrganizerId(eventId, organizerId).orElseThrow(
				() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
		event.requireDraft();
		requireOrganizerCanPublish(organizerId);
		requireComplete(event);
		Instant now = clock.instant();
		if (!now.isBefore(event.getEndsAt())) {
			throw new BusinessException(ErrorCode.INVALID_EVENT_TIMES, "Sự kiện đã qua giờ kết thúc, không publish được");
		}

		int feeBps = catalog.feeBpsAt(now).orElse(properties.defaultPlatformFeeBps());
		event.publish(feeBps);
		catalog.lockSeatMap(event.getSeatMapId());
		events.save(event);

		VenueInfo venue = catalog.venueInfo(event.getVenueId()).orElseThrow();
		publisher.publishEvent(new EventPublished(Uuid7.next(clock), now, event.getId(), organizerId, event.getTitle(),
				event.getDescription(), event.getCategory(), event.getBannerUrl(), event.getVenueId(), venue.name(),
				venue.cityId(), venue.cityName(), event.getSeatMapId(), event.getStartsAt(), event.getEndsAt(),
				event.getSaleStartsAt(), event.getMaxTicketsPerUser(), feeBps, event.isHighDemand()));
		log.info("Sự kiện {} đã publish, phí nền tảng {} bps", event.getId(), feeBps);
		return eventService.get(organizerId, eventId);
	}

	/**
	 * Chạy theo lịch: sự kiện qua giờ kết thúc chuyển ENDED; sự kiện tới giờ mở bán và có tồn kho chuyển ON_SALE. Theo
	 * lô, chạy lại không gây sai vì chỉ chọn sự kiện còn ở trạng thái đủ điều kiện.
	 *
	 * @return số sự kiện đã chuyển trạng thái
	 */
	@Transactional
	public int advance() {
		Instant now = clock.instant();
		int changed = 0;
		for (Event event : events.findTop100ByStatusInAndEndsAtLessThanEqualOrderByEndsAt(ENDABLE, now)) {
			event.end();
			publisher.publishEvent(new EventEnded(Uuid7.next(clock), now, event.getId()));
			log.info("Sự kiện {} đã kết thúc", event.getId());
			changed++;
		}
		List<Event> readyForSale = new ArrayList<>(
				events.findTop100ByStatusAndInventoryReadyTrueAndSaleStartsAtLessThanEqualOrderBySaleStartsAt(
						EventStatus.PUBLISHED, now));
		for (Event event : readyForSale) {
			event.startSale(now);
			publisher.publishEvent(new EventOnSale(Uuid7.next(clock), now, event.getId()));
			log.info("Sự kiện {} bắt đầu mở bán", event.getId());
			changed++;
		}
		return changed;
	}

	/** Booking đã tạo xong tồn kho ghế. Nhận nhiều lần cũng được: chỉ đặt cờ. */
	@Transactional
	public void markInventoryReady(UUID eventId) {
		events.findById(eventId).ifPresentOrElse(event -> {
			event.markInventoryReady();
			log.info("Tồn kho ghế của sự kiện {} đã sẵn sàng", eventId);
		}, () -> log.warn("Nhận SeatInventoryReady cho sự kiện không tồn tại: {}", eventId));
	}

	private void requireOrganizerCanPublish(UUID organizerId) {
		OrganizerInfo organizer = catalog.organizer(organizerId).orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZER_NOT_REGISTERED, "Tài khoản chưa đăng ký ban tổ chức"));
		List<String> reasons = new ArrayList<>();
		if (!"APPROVED".equals(organizer.status())) {
			reasons.add("ORGANIZER_NOT_APPROVED");
		}
		if (!organizer.payoutReady()) {
			reasons.add("PAYOUT_NOT_READY");
		}
		if (!reasons.isEmpty()) {
			// BR-05: phải được Admin duyệt và tài khoản Stripe Connect đã nhận được tiền.
			throw new BusinessException(ErrorCode.PUBLISH_NOT_ALLOWED, "Chưa đủ điều kiện publish")
					.with("reasons", reasons);
		}
	}

	private void requireComplete(Event event) {
		List<String> missing = new ArrayList<>();
		if (event.getVenueId() == null) {
			missing.add("VENUE");
		}
		if (event.getSeatMapId() == null) {
			missing.add("SEAT_MAP");
		}
		if (event.getStartsAt() == null) {
			missing.add("STARTS_AT");
		}
		if (event.getEndsAt() == null) {
			missing.add("ENDS_AT");
		}
		if (event.getSaleStartsAt() == null) {
			missing.add("SALE_STARTS_AT");
		}
		if (tiers.findByEventIdOrderById(event.getId()).isEmpty()) {
			missing.add("TICKET_TIERS");
		}
		if (tierSections.findByEventId(event.getId()).isEmpty()) {
			missing.add("TIER_SECTIONS");
		}
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.PUBLISH_INCOMPLETE, "Còn thiếu thông tin để publish").with("missing",
					missing);
		}
	}

}
