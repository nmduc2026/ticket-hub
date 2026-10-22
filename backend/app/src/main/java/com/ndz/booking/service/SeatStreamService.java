package com.ndz.booking.service;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Luồng SSE trạng thái ghế của một sự kiện cho người đang xem sơ đồ (overview.md §12). */
public interface SeatStreamService {

	/**
	 * Mở luồng {@code seats {changes: [{seatId, status}]}}. Luồng chỉ mang thay đổi: client nối rồi tải snapshot
	 * {@code GET /events/{id}/seats}, kết nối lại thì tải lại snapshot.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code EVENT_NOT_FOUND}
	 */
	SseEmitter open(UUID eventId);

}
