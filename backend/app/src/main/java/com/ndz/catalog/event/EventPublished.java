package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Sự kiện được publish (BR-05, BR-06, BR-14). Chỉ mang metadata: danh sách ghế không đi trong event (ADR 0021),
 * Booking lấy qua interface công khai của Catalog. {@code platformFeeBps} đã chốt lúc publish (ADR 0023).
 */
public record EventPublished(UUID messageId, Instant occurredAt, UUID eventId, UUID organizerId, String title,
		String description, String category, String bannerUrl, UUID venueId, String venueName, UUID cityId, String cityName,
		UUID seatMapId, Instant startsAt, Instant endsAt, Instant saleStartsAt, int maxTicketsPerUser,
		int platformFeeBps, boolean highDemand, Instant doorsOpenAt, Short ageRestriction, String rules)
		implements IntegrationEvent {
}
