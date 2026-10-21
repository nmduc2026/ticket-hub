package com.ndz.backend.catalog.internal.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.catalog.internal.persistence.CatalogReadRepository;
import com.ndz.backend.catalog.internal.domain.CatalogViews.ElementRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.SeatMapRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.SectionRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.VenueRow;
import com.ndz.backend.shared.exception.BusinessException;
import com.ndz.backend.shared.exception.ErrorCode;

/** Địa điểm và sơ đồ ghế cho organizer chọn khi tạo sự kiện (overview.md §11.2). Chỉ đọc. */
@Service
public class VenueQueryService {

	/** Một sơ đồ ghế kèm các khu và phần tử không bán để frontend vẽ. */
	public record SeatMapView(SeatMapRow map, List<SectionRow> sections, List<ElementRow> elements) {
	}

	private final CatalogReadRepository catalog;

	VenueQueryService(CatalogReadRepository catalog) {
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public List<VenueRow> venues() {
		return catalog.listVenues();
	}

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
