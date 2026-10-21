package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.response.CatalogViews.SeatMapView;
import com.ndz.catalog.dto.response.CatalogViews.VenueRow;

/** Địa điểm và sơ đồ ghế cho organizer chọn khi tạo sự kiện (overview.md §11.2). Chỉ đọc. */
public interface VenueQueryService {

	List<VenueRow> venues();

	/** Các sơ đồ ghế của địa điểm kèm khu và phần tử không bán; ném {@code VENUE_NOT_FOUND} nếu không có địa điểm. */
	List<SeatMapView> seatMaps(UUID venueId);

}
