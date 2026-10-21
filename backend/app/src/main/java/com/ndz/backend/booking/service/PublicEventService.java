package com.ndz.backend.booking.service;

import java.util.UUID;

import com.ndz.backend.booking.dto.response.EventViews.EventPage;
import com.ndz.backend.booking.dto.response.EventViews.EventPageView;
import com.ndz.backend.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.backend.booking.dto.response.EventViews.SeatMapState;

/**
 * Đọc công khai về sự kiện cho khách chưa đăng nhập (overview.md §11.2). Sơ đồ ghế lấy từ Catalog qua interface công
 * khai (ADR 0018), trạng thái ghế từ tồn kho của Booking.
 */
public interface PublicEventService {

	EventPage search(SearchCriteria criteria, int page, int size);

	/** Chi tiết sự kiện kèm hạng vé và còn vé hay không. */
	EventPageView detail(UUID eventId);

	/** Sơ đồ ghế kèm trạng thái: ghép bố cục của Catalog với tồn kho của Booking. */
	SeatMapState seats(UUID eventId);

}
