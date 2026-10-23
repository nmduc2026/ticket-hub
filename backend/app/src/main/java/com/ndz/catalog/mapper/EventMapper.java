package com.ndz.catalog.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ndz.catalog.dto.request.EventRequest;
import com.ndz.catalog.dto.response.EventResponses.Assignment;
import com.ndz.catalog.dto.response.EventResponses.Detail;
import com.ndz.catalog.dto.response.EventResponses.Summary;
import com.ndz.catalog.dto.response.EventResponses.Tier;
import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.EventContent;
import com.ndz.catalog.entity.TicketTier;
import com.ndz.catalog.entity.TierSection;
import com.ndz.catalog.entity.enums.EventStatus;

/** Chuyển giữa request / response của API sự kiện và entity; entity không ra khỏi service. */
@Component
public class EventMapper {

	/** Mặc định 6 vé mỗi người cho mỗi sự kiện (BR-02). */
	private static final short DEFAULT_MAX_TICKETS_PER_USER = 6;

	public EventContent toContent(EventRequest request) {
		return new EventContent(request.title().trim(), request.description(), request.category(), request.venueId(),
				request.seatMapId(), request.startsAt(), request.endsAt(), request.saleStartsAt(),
				request.maxTicketsPerUser() == null ? DEFAULT_MAX_TICKETS_PER_USER : request.maxTicketsPerUser(),
				request.doorsOpenAt(), request.ageRestriction(), blankToNull(request.rules()),
				Boolean.TRUE.equals(request.highDemand()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public Summary toSummary(Event event, String venueName) {
		return new Summary(event.getId(), event.getTitle(), event.getCategory(), event.getBannerUrl(),
				event.getStatus(), event.getStartsAt(), event.getEndsAt(), event.getSaleStartsAt(), event.getVenueId(),
				venueName);
	}

	public Detail toDetail(Event event, String venueName, List<TicketTier> tiers, List<TierSection> assignments) {
		return new Detail(event.getId(), event.getTitle(), event.getDescription(), event.getCategory(),
				event.getBannerUrl(), event.getStatus(), event.getVenueId(), venueName, event.getSeatMapId(),
				event.getStartsAt(), event.getEndsAt(), event.getSaleStartsAt(), event.getMaxTicketsPerUser(),
				event.getPlatformFeeBps(), event.isInventoryReady(), event.getStatus() != EventStatus.DRAFT,
				tiers.stream().map(this::toTier).toList(), assignments.stream().map(this::toAssignment).toList(),
				event.getDoorsOpenAt(), event.getAgeRestriction(), event.getRules(), event.isHighDemand());
	}

	public Tier toTier(TicketTier tier) {
		return new Tier(tier.getId(), tier.getName(), tier.getPriceMinor(), tier.getCurrency());
	}

	public Assignment toAssignment(TierSection section) {
		return new Assignment(section.getSectionId(), section.getTierId());
	}

}
