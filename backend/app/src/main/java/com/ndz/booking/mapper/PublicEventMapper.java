package com.ndz.booking.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.ndz.booking.dto.response.EventViews.EventListItem;
import com.ndz.booking.dto.response.EventViews.EventPage;
import com.ndz.booking.dto.response.EventViews.EventPageView;
import com.ndz.booking.dto.response.EventViews.SeatMapState;
import com.ndz.booking.dto.response.EventViews.SeatState;
import com.ndz.booking.dto.response.EventViews.TierAvailability;
import com.ndz.booking.dto.response.PublicEventResponses;
import com.ndz.booking.dto.response.PublicEventResponses.Detail;
import com.ndz.booking.dto.response.PublicEventResponses.Element;
import com.ndz.booking.dto.response.PublicEventResponses.Page;
import com.ndz.booking.dto.response.PublicEventResponses.Seat;
import com.ndz.booking.dto.response.PublicEventResponses.Seats;
import com.ndz.booking.dto.response.PublicEventResponses.Section;
import com.ndz.booking.dto.response.PublicEventResponses.Summary;
import com.ndz.booking.dto.response.PublicEventResponses.Tier;
import com.ndz.catalog.api.CatalogApi.LayoutElement;
import com.ndz.catalog.api.CatalogApi.LayoutSeat;
import com.ndz.catalog.api.CatalogApi.LayoutSection;

/** Chuyển view đọc của sự kiện công khai sang response; {@code shape_json} thành JSON. */
@Component
public class PublicEventMapper {

	private final ObjectMapper json;

	PublicEventMapper(ObjectMapper json) {
		this.json = json;
	}

	public Page toPage(EventPage page) {
		return new Page(page.items().stream().map(this::toSummary).toList(), page.total(), page.page(), page.size());
	}

	public Detail toDetail(EventPageView view) {
		var event = view.event();
		return new Detail(event.id(), event.title(), event.description(), event.category(), event.bannerUrl(),
				event.organizerId(), event.venueName(), event.cityName(), event.startsAt(), event.endsAt(),
				event.saleStartsAt(), event.status(), event.maxTicketsPerUser(), view.soldOut(),
				view.tiers().stream().map(this::toTier).toList());
	}

	public Seats toSeats(SeatMapState state) {
		List<Section> sections = state.layout().sections().stream().map(section -> toSection(section, state))
				.toList();
		List<Element> elements = state.layout().elements().stream().map(this::toElement).toList();
		List<Seat> seats = state.layout().seats().stream().map(seat -> toSeat(seat, state)).toList();
		return new Seats(state.event().id(), state.event().status(), state.layout().width(), state.layout().height(),
				state.tiers().stream().map(this::toTier).toList(), sections, elements, seats);
	}

	private Summary toSummary(EventListItem item) {
		return new Summary(item.id(), item.title(), item.bannerUrl(), item.startsAt(), item.venueName(),
				item.cityName(), item.minPriceMinor(), item.currency(), item.status());
	}

	private Tier toTier(TierAvailability tier) {
		return new Tier(tier.tierId(), tier.name(), tier.priceMinor(), tier.currency(), tier.available());
	}

	private Section toSection(LayoutSection section, SeatMapState state) {
		return new Section(section.code(), section.name(), json.readTree(section.shapeJson()),
				state.tierBySectionCode().get(section.code()));
	}

	private Element toElement(LayoutElement element) {
		return new Element(element.type(), element.label(), json.readTree(element.shapeJson()));
	}

	private Seat toSeat(LayoutSeat seat, SeatMapState state) {
		SeatState inventory = state.seatStates().get(seat.id());
		return new Seat(seat.id(), seat.sectionCode(), seat.rowLabel(), seat.seatNumber(), seat.x(), seat.y(),
				inventory == null ? PublicEventResponses.NOT_FOR_SALE : inventory.status());
	}

}
