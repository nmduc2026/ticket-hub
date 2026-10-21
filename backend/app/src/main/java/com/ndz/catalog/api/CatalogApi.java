package com.ndz.catalog.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface công khai của module Catalog cho module khác gọi đồng bộ (ADR 0018, 0021). Khi tách service, thay cài đặt
 * bằng lời gọi API nội bộ; code gọi giữ nguyên.
 */
public interface CatalogApi {

	/**
	 * Các ghế đang bán của một sự kiện: ghế thuộc khu đã gán hạng vé, kèm hạng vé và giá. Khu không gán hạng vé không
	 * có trong kết quả (không bán, overview.md §6.1).
	 */
	List<SellableSeat> sellableSeats(UUID eventId);

	/**
	 * Toàn bộ sơ đồ ghế để vẽ: kích thước, các khu, phần tử không bán (sân khấu, lối đi) và mọi ghế kèm toạ độ, kể cả
	 * ghế của khu không bán. Trạng thái và hạng vé của ghế do Booking ghép từ tồn kho.
	 */
	SeatMapLayout seatMapLayout(UUID seatMapId);

	/** Người dùng này có phải organizer sở hữu sự kiện không (check-in chỉ dành cho organizer của sự kiện). */
	boolean isEventOrganizer(UUID eventId, UUID userId);

	/** Giờ diễn và giờ kết thúc của sự kiện (check-in chỉ mở quanh giờ diễn, BR-09). Rỗng nếu không có sự kiện. */
	Optional<EventSchedule> eventSchedule(UUID eventId);

	/** {@code startsAt}, {@code endsAt} có thể trống khi sự kiện còn nháp. */
	record EventSchedule(Instant startsAt, Instant endsAt) {
	}

	/**
	 * Thông tin cho khách hiện tại của các sự kiện (sửa được sau publish nên không chụp lại vào vé). Sự kiện không có
	 * thì không có trong kết quả.
	 */
	Map<UUID, GuestInfo> guestInfo(Collection<UUID> eventIds);

	/** Mọi trường có thể trống. */
	record GuestInfo(Instant doorsOpenAt, Short ageRestriction, String rules) {
	}

	/** Sự kiện của một organizer, mới diễn trước, tối đa {@code limit} dòng (Admin xem chi tiết người dùng). */
	List<OrganizerEvent> eventsOf(UUID organizerId, int limit);

	record OrganizerEvent(UUID eventId, String title, String status, Instant startsAt) {
	}

	/** Sơ đồ ghế; {@code shapeJson} là JSON nguyên văn của hình học khu / phần tử. */
	record SeatMapLayout(int width, int height, List<LayoutSection> sections, List<LayoutElement> elements,
			List<LayoutSeat> seats) {
	}

	record LayoutSection(UUID id, String code, String name, String shapeJson) {
	}

	record LayoutElement(UUID id, String type, String label, String shapeJson) {
	}

	record LayoutSeat(UUID id, UUID sectionId, String sectionCode, String rowLabel, int seatNumber, int x, int y) {
	}

	/** Một ghế đang bán và hạng vé của nó. Giá là số nguyên đơn vị nhỏ nhất (BR-10). */
	record SellableSeat(UUID seatId, UUID sectionId, String sectionCode, String rowLabel, int seatNumber,
			UUID tierId, String tierName, long priceMinor, String currency) {
	}

}
