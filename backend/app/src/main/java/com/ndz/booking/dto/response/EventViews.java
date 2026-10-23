package com.ndz.booking.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ndz.catalog.api.CatalogApi.SeatMapLayout;

/** Giá trị chỉ đọc cho API công khai về sự kiện, dựng từ {@code event_snapshots} và {@code seat_inventory}. */
public final class EventViews {

	private EventViews() {
	}

	/** Điều kiện tìm sự kiện; trường null nghĩa là không lọc. */
	public record SearchCriteria(String query, UUID cityId, String category, Instant from, Instant to) {
	}

	public record EventListItem(UUID id, String title, String bannerUrl, Instant startsAt, String venueName,
			String cityName, Long minPriceMinor, String currency, String status) {
	}

	public record EventPage(java.util.List<EventListItem> items, long total, int page, int size) {
	}

	public record EventDetail(UUID id, String title, String description, String category, String bannerUrl,
			UUID organizerId, UUID venueId, String venueName, String cityName, Instant startsAt, Instant endsAt,
			Instant saleStartsAt, String status, boolean highDemand, int maxTicketsPerUser, UUID seatMapId,
			String currency, Instant doorsOpenAt, Short ageRestriction, String rules) {
	}

	/** Một hạng vé của sự kiện và số ghế còn trống của hạng đó. */
	public record TierAvailability(UUID tierId, String name, long priceMinor, String currency, int total,
			int available) {
	}

	/** Trạng thái một ghế trong tồn kho. */
	public record SeatState(UUID seatId, UUID tierId, String status) {
	}

	/** Chi tiết sự kiện kèm hạng vé và còn vé hay không. */
	public record EventPageView(EventDetail event, List<TierAvailability> tiers, boolean soldOut) {
	}

	/** Sơ đồ ghế kèm trạng thái: ghép bố cục của Catalog với tồn kho của Booking. */
	public record SeatMapState(EventDetail event, SeatMapLayout layout, List<TierAvailability> tiers,
			Map<String, UUID> tierBySectionCode, Map<UUID, SeatState> seatStates) {
	}

}
