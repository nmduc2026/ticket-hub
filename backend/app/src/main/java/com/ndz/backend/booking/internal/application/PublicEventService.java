package com.ndz.backend.booking.internal.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.booking.internal.domain.EventViews.EventDetail;
import com.ndz.backend.booking.internal.domain.EventViews.EventPage;
import com.ndz.backend.booking.internal.domain.EventViews.SearchCriteria;
import com.ndz.backend.booking.internal.domain.EventViews.SeatState;
import com.ndz.backend.booking.internal.domain.EventViews.TierAvailability;
import com.ndz.backend.booking.internal.persistence.PublicEventQueries;
import com.ndz.backend.catalog.api.CatalogApi;
import com.ndz.backend.catalog.api.CatalogApi.SeatMapLayout;
import com.ndz.backend.shared.exception.BusinessException;
import com.ndz.backend.shared.exception.ErrorCode;

/**
 * Đọc công khai về sự kiện cho khách chưa đăng nhập (overview.md §11.2). Sơ đồ ghế lấy từ Catalog qua interface công
 * khai (ADR 0018), trạng thái ghế từ tồn kho của Booking.
 */
@Service
public class PublicEventService {

	private static final int MAX_PAGE_SIZE = 50;

	/** Chi tiết sự kiện kèm hạng vé và còn vé hay không. */
	public record EventPageView(EventDetail event, List<TierAvailability> tiers, boolean soldOut) {
	}

	/** Sơ đồ ghế kèm trạng thái: ghép bố cục của Catalog với tồn kho của Booking. */
	public record SeatMapState(EventDetail event, SeatMapLayout layout, List<TierAvailability> tiers,
			Map<String, UUID> tierBySectionCode, Map<UUID, SeatState> seatStates) {
	}

	private final PublicEventQueries queries;
	private final CatalogApi catalog;
	private final Clock clock;

	PublicEventService(PublicEventQueries queries, CatalogApi catalog, Clock clock) {
		this.queries = queries;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public EventPage search(SearchCriteria criteria, int page, int size) {
		return queries.search(criteria, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				clock.instant());
	}

	@Transactional(readOnly = true)
	public EventPageView detail(UUID eventId) {
		EventDetail event = find(eventId);
		List<TierAvailability> tiers = queries.tiers(eventId);
		// "Hết vé" không lưu trong DB: tính từ số ghế còn trống, vì hoàn vé có thể làm ghế trống trở lại (§7.7).
		boolean soldOut = !tiers.isEmpty() && tiers.stream().allMatch(tier -> tier.available() == 0);
		return new EventPageView(event, tiers, soldOut);
	}

	@Transactional(readOnly = true)
	public SeatMapState seats(UUID eventId) {
		EventDetail event = find(eventId);
		return new SeatMapState(event, catalog.seatMapLayout(event.seatMapId()), queries.tiers(eventId),
				queries.tierBySectionCode(eventId), queries.seatStates(eventId));
	}

	private EventDetail find(UUID eventId) {
		return queries.detail(eventId).orElseThrow(
				() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
	}

}
