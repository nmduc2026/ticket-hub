package com.ndz.catalog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.catalog.entity.enums.EventStatus;

/** Các response của API sự kiện cho organizer; entity không bao giờ ra khỏi lớp service. */
public final class EventResponses {

	private EventResponses() {
	}

	@Schema(name = "OrganizerEventSummary")
	public record Summary(
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

	}

	@Schema(name = "OrganizerEventDetail")
	public record Detail(
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
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Assignment> tierSections,
			Instant doorsOpenAt,
			Short ageRestriction,
			String rules,
			@Schema(description = "Bật phòng chờ (BR-04); khoá sau publish",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean highDemand) {

	}

	@Schema(name = "TicketTier")
	public record Tier(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Đơn vị nhỏ nhất của tiền tệ (VND không nhân 100)",
					requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency) {

	}

	@Schema(name = "TierSectionAssignment")
	public record Assignment(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sectionId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID tierId) {

	}

	@Schema(name = "OrganizerEventPage")
	public record EventPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {
	}

}
