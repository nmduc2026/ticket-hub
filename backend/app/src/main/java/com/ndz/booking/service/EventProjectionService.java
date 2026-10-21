package com.ndz.booking.service;

import com.ndz.catalog.event.EventEnded;
import com.ndz.catalog.event.EventOnSale;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventUpdated;

/**
 * Dựng và giữ bản sao sự kiện ({@code event_snapshots}) cùng tồn kho ghế theo event của Catalog (overview.md §6.2,
 * §7.9, ADR 0021): Booking kiểm giờ mở bán, trạng thái, giới hạn vé từ bản sao này, không đọc DB của Catalog. Mỗi hàm
 * idempotent: ghi {@code processed_events} cùng transaction với xử lý; event cũ hơn dữ liệu đang có bị bỏ qua. Người
 * gọi (listener) mở transaction.
 */
public interface EventProjectionService {

	/**
	 * Xử lý {@code EventPublished} theo thứ tự: dựng bản sao, lấy danh sách ghế đang bán qua interface công khai của
	 * Catalog, ghi {@code seat_inventory} idempotent, rồi báo {@code SeatInventoryReady}. Gộp một hàm vì tồn kho tham
	 * chiếu bản sao sự kiện. Nhận event lần hai không tạo thêm gì và không báo lại.
	 */
	void onPublished(EventPublished event);

	void onUpdated(EventUpdated event);

	void onOnSale(EventOnSale event);

	void onEnded(EventEnded event);

}
