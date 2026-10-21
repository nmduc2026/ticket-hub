package com.ndz.booking.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;


/** Response của API công khai về sự kiện. */
public final class PublicEventResponses {

	/** Trạng thái hiển thị của một ghế; {@code NOT_FOR_SALE} là ghế của khu không gán hạng vé (màu xám). */
	public static final String NOT_FOR_SALE = "NOT_FOR_SALE";

	private PublicEventResponses() {
	}

	@Schema(name = "PublicEventSummary")
	public record Summary(
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

	}

	@Schema(name = "PublicEventPage")
	public record Page(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {

	}

	@Schema(name = "PublicTier")
	public record Tier(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Đơn vị nhỏ nhất của tiền tệ", requiredMode = Schema.RequiredMode.REQUIRED) long priceMinor,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
			@Schema(description = "Số ghế còn trống của hạng vé", requiredMode = Schema.RequiredMode.REQUIRED) int available) {

	}

	@Schema(name = "PublicEventDetail")
	public record Detail(
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
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Tier> tiers,
			@Schema(description = "Giờ mở cửa; trống nếu ban tổ chức chưa nhập") Instant doorsOpenAt,
			@Schema(description = "Số tuổi tối thiểu; trống là không giới hạn") Short ageRestriction,
			@Schema(description = "Quy định khi tham dự") String rules) {

	}

	@Schema(name = "SeatMapSectionState")
	public record Section(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Hình học của khu (GRID / IMPORTED), nguyên văn",
					requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape,
			@Schema(description = "Hạng vé của khu; null khi khu không bán (màu xám)") UUID tierId) {
	}

	@Schema(name = "SeatMapElementState")
	public record Element(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
			String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {
	}

	@Schema(name = "SeatState")
	public record Seat(
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
	public record Seats(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventStatus,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int width,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int height,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Tier> tiers,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Section> sections,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Element> elements,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Seat> seats) {
	}

}
