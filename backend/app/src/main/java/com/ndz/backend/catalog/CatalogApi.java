package com.ndz.backend.catalog;

import java.util.List;
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
