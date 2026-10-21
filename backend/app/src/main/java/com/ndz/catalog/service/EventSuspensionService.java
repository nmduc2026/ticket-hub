package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.dto.response.SuspensionResponses.EventStatusResult;
import com.ndz.catalog.dto.response.SuspensionResponses.OrganizerSuspension;

/**
 * Tạm dừng bán (BR-13): Admin tạm dừng / mở lại sự kiện, đình chỉ / khôi phục organizer; tự tạm dừng mọi sự kiện đang
 * bán khi organizer bị đình chỉ hoặc mất {@code charges_enabled}. Mọi thao tác của Admin ghi audit.
 */
public interface EventSuspensionService {

	/** {@code INVALID_EVENT_TRANSITION} nếu sự kiện không ở {@code PUBLISHED} / {@code ON_SALE}. */
	EventStatusResult suspendEvent(UUID adminId, UUID eventId, String reason);

	/** Mỗi id xử lý riêng; kết quả theo từng id. */
	BulkResult bulkSuspend(UUID adminId, List<UUID> eventIds, String reason);

	/**
	 * Về {@code ON_SALE} nếu đã qua giờ mở bán, ngược lại {@code PUBLISHED}. {@code ORGANIZER_SUSPENDED} khi organizer
	 * còn bị đình chỉ, {@code ORGANIZER_PAYOUT_NOT_READY} khi chưa {@code charges_enabled}.
	 */
	EventStatusResult resumeEvent(UUID adminId, UUID eventId);

	/** Đình chỉ organizer và tạm dừng mọi sự kiện {@code PUBLISHED} / {@code ON_SALE} của họ. */
	OrganizerSuspension suspendOrganizer(UUID adminId, UUID organizerId, String reason);

	/** Khôi phục organizer về {@code APPROVED}; sự kiện vẫn tạm dừng tới khi Admin mở lại từng cái. */
	OrganizerSuspension reinstateOrganizer(UUID adminId, UUID organizerId, String reason);

}
