package com.ndz.booking.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.service.PublicEventService;
import com.ndz.booking.dto.response.EventViews.EventDetail;
import com.ndz.booking.dto.response.EventViews.EventPage;
import com.ndz.booking.dto.response.EventViews.EventPageView;
import com.ndz.booking.dto.response.EventViews.SeatMapState;
import com.ndz.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.booking.dto.response.EventViews.TierAvailability;
import com.ndz.booking.repository.PublicEventQueries;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Đọc công khai về sự kiện cho khách chưa đăng nhập (overview.md §11.2). Sơ đồ ghế lấy từ Catalog qua interface công
 * khai (ADR 0018), trạng thái ghế từ tồn kho của Booking.
 */
@Service
public class PublicEventServiceImpl implements PublicEventService {

	private static final int MAX_PAGE_SIZE = 50;

	private final PublicEventQueries queries;
	private final CatalogApi catalog;
	private final Clock clock;

	PublicEventServiceImpl(PublicEventQueries queries, CatalogApi catalog, Clock clock) {
		this.queries = queries;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public EventPage search(SearchCriteria criteria, int page, int size) {
		return queries.search(criteria, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				clock.instant());
	}

	@Override
	@Transactional(readOnly = true)
	public EventPageView detail(UUID eventId) {
		EventDetail event = find(eventId);
		List<TierAvailability> tiers = queries.tiers(eventId);
		// "Hết vé" không lưu trong DB: tính từ số ghế còn trống, vì hoàn vé có thể làm ghế trống trở lại (§7.7).
		boolean soldOut = !tiers.isEmpty() && tiers.stream().allMatch(tier -> tier.available() == 0);
		return new EventPageView(event, tiers, soldOut);
	}

	@Override
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
