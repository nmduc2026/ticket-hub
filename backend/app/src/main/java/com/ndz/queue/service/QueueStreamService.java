package com.ndz.queue.service;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Luồng SSE của phòng chờ (overview.md §7.1, §12): vị trí, được gọi vào, đóng phòng chờ. */
public interface QueueStreamService {

	/**
	 * Mở luồng cho khách đã vào hàng. Gửi ngay trạng thái hiện tại nên kết nối lại đúng lúc được gọi tên nhận
	 * {@code admitted} ngay.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code QUEUE_NOT_JOINED}, {@code QUEUE_CLOSED},
	 *         {@code QUEUE_NOT_ENABLED} hoặc {@code EVENT_NOT_FOUND}, như {@code status}
	 */
	SseEmitter open(UUID eventId, UUID userId);

	/** Đẩy vị trí mới nhất cho các kết nối của pod này (vị trí đổi khi người đứng trước được gọi). */
	void pushPositions();

}
