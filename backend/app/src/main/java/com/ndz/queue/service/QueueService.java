package com.ndz.queue.service;

import java.time.Instant;
import java.util.UUID;

import com.ndz.queue.dto.response.QueueStatusResponse;

/** Phòng chờ ảo (overview.md §7.1, BR-04). */
public interface QueueService {

	/** Vào hàng chờ; vào lại không đổi vị trí, đang có lượt vào thì trả {@code ADMITTED}. */
	QueueStatusResponse join(UUID eventId, UUID userId);

	QueueStatusResponse status(UUID eventId, UUID userId);

	/** Ghi lại thông tin sự kiện khi publish (từ event của Catalog). */
	void registerEvent(UUID eventId, boolean highDemand, Instant saleStartsAt);

	/**
	 * Đóng phòng chờ khi sự kiện bị tạm dừng, huỷ hoặc kết thúc: báo {@code closed {reason}} cho mọi người đang nghe
	 * SSE rồi xoá các key {@code queue:{eventId}:*}.
	 */
	void close(UUID eventId, CloseReason reason);

	enum CloseReason {
		SUSPENDED, CANCELLED, ENDED
	}

}
