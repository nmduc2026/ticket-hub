package com.ndz.backend.booking.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.ndz.backend.booking.internal.application.PublicEventService.EventPageView;
import com.ndz.backend.booking.internal.application.PublicEventService.SeatMapState;
import com.ndz.backend.booking.internal.domain.EventViews.EventListItem;
import com.ndz.backend.booking.internal.domain.EventViews.EventPage;
import com.ndz.backend.booking.internal.domain.EventViews.SeatState;
import com.ndz.backend.booking.internal.domain.EventViews.TierAvailability;
import com.ndz.backend.catalog.CatalogApi.LayoutElement;
import com.ndz.backend.catalog.CatalogApi.LayoutSeat;
import com.ndz.backend.catalog.CatalogApi.LayoutSection;

/** Response của API công khai về sự kiện. */
final class PublicEventResponses {

	/** Trạng thái hiển thị của một ghế; {@code NOT_FOR_SALE} là ghế của khu không gán hạng vé (màu xám). */
	static final String NOT_FOR_SALE = "NOT_FOR_SALE";

	private PublicEventResponses() {
	}

	@Schema(name = "PublicEventSummary")
	record Summary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			String bannerUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String venueName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
			@Schema(description = "Giá thấp nhất của các hạng vé; null khi chưa có tồn kho") Long minPriceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(description = "PUBLISHED (chờ mở bán), ON_SALE hoặc SUSPENDED",
					requiredMode = Schema.RequiredMode.REQUIRED) String status) {

		static Summary from(EventListItem item) {
			return new Summary(item.id(), item.title(), item.bannerUrl(), item.startsAt(), item.venueName(),
					item.cityName(), item.minPriceMinor(), item.currency(), item.status());
		}

	}

	@Schema(name = "PublicEventPage")
	record Page(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {

		static Page from(EventPage page) {
			return new Page(page.items().stream().map(Summary::from).toList(), page.total(), page.page(),
					page.size());
		}

	}

	@Schema(name = "PublicTier")
	record Tier(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Đơn vị nhỏ nhất của tiền tệ", requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(description = "Số ghế còn trống của hạng vé", requiredMode = Schema.RequiredMode.REQUIRED) int available) {

		static Tier from(TierAvailability tier) {
			return new Tier(tier.tierId(), tier.name(), tier.priceMinor(), tier.currency(), tier.available());
		}

	}

	@Schema(name = "PublicEventDetail")
	record Detail(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			String description,
			String category,
			String bannerUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID organizerId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String venueName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant saleStartsAt,
			@Schema(description = "PUBLISHED, ON_SALE, SUSPENDED, ENDED hoặc CANCELLED",
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxTicketsPerUser,
			@Schema(description = "true khi mọi hạng vé hết ghế (tính từ tồn kho, không lưu trong DB)",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean soldOut,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Tier> tiers) {

		static Detail from(EventPageView view) {
			var event = view.event();
			return new Detail(event.id(), event.title(), event.description(), event.category(), event.bannerUrl(),
					event.organizerId(), event.venueName(), event.cityName(), event.startsAt(), event.endsAt(),
					event.saleStartsAt(), event.status(), event.maxTicketsPerUser(), view.soldOut(),
					view.tiers().stream().map(Tier::from).toList());
		}

	}

	@Schema(name = "SeatMapSectionState")
	record Section(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Hình học của khu (GRID / IMPORTED), nguyên văn",
					requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape,
			@Schema(description = "Hạng vé của khu; null khi khu không bán (màu xám)") UUID tierId) {
	}

	@Schema(name = "SeatMapElementState")
	record Element(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
			String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {
	}

	@Schema(name = "SeatState")
	record Seat(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sectionCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String row,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int x,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int y,
			@Schema(description = "AVAILABLE, HELD, SOLD hoặc NOT_FOR_SALE",
					requiredMode = Schema.RequiredMode.REQUIRED) String status) {
	}

	@Schema(name = "EventSeats")
	record Seats(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventStatus,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int width,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int height,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Tier> tiers,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Section> sections,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Element> elements,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Seat> seats) {

		static Seats from(SeatMapState state, ObjectMapper json) {
			List<Section> sections = state.layout().sections().stream().map(section -> section(section, state, json))
					.toList();
			List<Element> elements = state.layout().elements().stream().map(element -> element(element, json))
					.toList();
			List<Seat> seats = state.layout().seats().stream().map(seat -> seat(seat, state)).toList();
			return new Seats(state.event().id(), state.event().status(), state.layout().width(),
					state.layout().height(), state.tiers().stream().map(Tier::from).toList(), sections, elements,
					seats);
		}

		private static Section section(LayoutSection section, SeatMapState state, ObjectMapper json) {
			return new Section(section.code(), section.name(), json.readTree(section.shapeJson()),
					state.tierBySectionCode().get(section.code()));
		}

		private static Element element(LayoutElement element, ObjectMapper json) {
			return new Element(element.type(), element.label(), json.readTree(element.shapeJson()));
		}

		private static Seat seat(LayoutSeat seat, SeatMapState state) {
			SeatState inventory = state.seatStates().get(seat.id());
			return new Seat(seat.id(), seat.sectionCode(), seat.rowLabel(), seat.seatNumber(), seat.x(), seat.y(),
					inventory == null ? NOT_FOR_SALE : inventory.status());
		}

	}

}
