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

	/** Một ghế đang bán và hạng vé của nó. Giá là số nguyên đơn vị nhỏ nhất (BR-10). */
	record SellableSeat(UUID seatId, UUID sectionId, String sectionCode, String rowLabel, int seatNumber,
			UUID tierId, String tierName, long priceMinor, String currency) {
	}

}
