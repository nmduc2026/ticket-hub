package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.dto.response.CatalogViews.ElementRow;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapRow;
import com.ndz.catalog.dto.response.CatalogViews.SectionRow;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapView;
import com.ndz.catalog.dto.response.CatalogViews.VenueRow;
import com.ndz.catalog.service.VenueQueryService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/** Địa điểm và sơ đồ ghế cho organizer chọn khi tạo sự kiện (overview.md §11.2). Chỉ đọc. */
@Service
public class VenueQueryServiceImpl implements VenueQueryService {

	private final CatalogReadRepository catalog;

	VenueQueryServiceImpl(CatalogReadRepository catalog) {
		this.catalog = catalog;
	}

	@Override
	@Transactional(readOnly = true)
	public List<VenueRow> venues() {
		return catalog.listVenues();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SeatMapView> seatMaps(UUID venueId) {
		if (!catalog.venueExists(venueId)) {
			throw new BusinessException(ErrorCode.VENUE_NOT_FOUND, "Không tìm thấy địa điểm");
		}
		List<SeatMapRow> maps = catalog.listSeatMaps(venueId);
		List<UUID> ids = maps.stream().map(SeatMapRow::id).toList();
		Map<UUID, List<SectionRow>> sections = catalog.listSections(ids).stream()
				.collect(Collectors.groupingBy(SectionRow::seatMapId));
		Map<UUID, List<ElementRow>> elements = catalog.listElements(ids).stream()
				.collect(Collectors.groupingBy(ElementRow::seatMapId));
		return maps.stream().map(map -> new SeatMapView(map, sections.getOrDefault(map.id(), List.of()),
				elements.getOrDefault(map.id(), List.of()))).toList();
	}

}
