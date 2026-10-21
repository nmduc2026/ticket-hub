package com.ndz.backend.catalog.service;

import java.util.UUID;

import com.ndz.backend.catalog.dto.response.EventResponses.Detail;

/**
 * Vòng đời sự kiện (overview.md §7.7, §7.9): publish, chuyển sang đang bán / kết thúc theo giờ, nhận tin tồn kho ghế
 * đã sẵn sàng.
 */
public interface EventLifecycleService {

	/** Organizer publish: kiểm BR-05 và đủ thông tin, chốt mức phí (BR-06), khoá thông tin bán vé (BR-14). */
	Detail publish(UUID organizerId, UUID eventId);

	/**
	 * Chạy theo lịch: sự kiện qua giờ kết thúc chuyển ENDED; sự kiện tới giờ mở bán và có tồn kho chuyển ON_SALE.
	 *
	 * @return số sự kiện đã chuyển trạng thái
	 */
	int advance();

	/** Booking đã tạo xong tồn kho ghế. Nhận nhiều lần cũng được: chỉ đặt cờ. */
	void markInventoryReady(UUID eventId);

}
