package com.ndz.backend.catalog.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventDetail;
import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventRow;
import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.EventStatus;
import com.ndz.backend.catalog.internal.domain.TicketTier;
import com.ndz.backend.catalog.internal.domain.TierSection;

/** Các response của API sự kiện cho organizer; entity không bao giờ ra khỏi lớp web. */
final class EventResponses {

	private EventResponses() {
	}

	@Schema(name = "OrganizerEventSummary")
	record Summary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			String category,
			String bannerUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventStatus status,
			Instant startsAt,
			Instant endsAt,
			Instant saleStartsAt,
			UUID venueId,
			String venueName) {

		static Summary from(EventRow row) {
			Event event = row.event();
			return new Summary(event.getId(), event.getTitle(), event.getCategory(), event.getBannerUrl(),
					event.getStatus(), event.getStartsAt(), event.getEndsAt(), event.getSaleStartsAt(),
					event.getVenueId(), row.venueName());
		}

	}

	@Schema(name = "OrganizerEventDetail")
	record Detail(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			String description,
			String category,
			String bannerUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventStatus status,
			UUID venueId,
			String venueName,
			UUID seatMapId,
			Instant startsAt,
			Instant endsAt,
			Instant saleStartsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxTicketsPerUser,
			Integer platformFeeBps,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inventoryReady,
			@Schema(description = "true khi đã publish: thông tin bán vé chỉ đọc (BR-14)",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean salesInfoLocked,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Tier> tiers,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Assignment> tierSections) {

		static Detail from(EventDetail detail) {
			Event event = detail.event();
			return new Detail(event.getId(), event.getTitle(), event.getDescription(), event.getCategory(),
					event.getBannerUrl(), event.getStatus(), event.getVenueId(), detail.venueName(),
					event.getSeatMapId(), event.getStartsAt(), event.getEndsAt(), event.getSaleStartsAt(),
					event.getMaxTicketsPerUser(), event.getPlatformFeeBps(), event.isInventoryReady(),
					event.getStatus() != EventStatus.DRAFT, detail.tiers().stream().map(Tier::from).toList(),
					detail.assignments().stream().map(Assignment::from).toList());
		}

	}

	@Schema(name = "TicketTier")
	record Tier(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Đơn vị nhỏ nhất của tiền tệ (VND không nhân 100)",
					requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {

		static Tier from(TicketTier tier) {
			return new Tier(tier.getId(), tier.getName(), tier.getPriceMinor(), tier.getCurrency());
		}

	}

	@Schema(name = "TierSectionAssignment")
	record Assignment(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sectionId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID tierId) {

		static Assignment from(TierSection section) {
			return new Assignment(section.getSectionId(), section.getTierId());
		}

	}

	@Schema(name = "OrganizerEventPage")
	record EventPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {
	}

}
