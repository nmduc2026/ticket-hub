package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.dto.response.SuspensionResponses.EventStatusResult;
import com.ndz.catalog.dto.response.SuspensionResponses.OrganizerSuspension;
import com.ndz.catalog.event.OrganizerPayoutStatusChanged;

/**
 * Tạm dừng bán (BR-13): Admin tạm dừng / mở lại sự kiện, đình chỉ / khôi phục organizer; tự tạm dừng mọi sự kiện đang
 * bán khi organizer bị đình chỉ hoặc mất {@code charges_enabled}. Mọi thao tác của Admin ghi audit.
 */
public interface EventSuspensionService {

	/** {@code INVALID_EVENT_TRANSITION} nếu sự kiện không ở {@code PUBLISHED} / {@code ON_SALE}. */
	EventStatusResult suspendEvent(UUID adminId, UUID eventId, String reason);

	/**
	 * Huỷ sự kiện (BR-08) từ {@code PUBLISHED} / {@code ON_SALE} / {@code SUSPENDED}; hoàn tiền do Booking và Payment
	 * làm tiếp qua {@code EventCancelled}. {@code INVALID_EVENT_TRANSITION} nếu đã kết thúc / đã huỷ.
	 */
	EventStatusResult cancelEvent(UUID adminId, UUID eventId, String reason);

	/** Mỗi id xử lý riêng; kết quả theo từng id. */
	BulkResult bulkSuspend(UUID adminId, List<UUID> eventIds, String reason);

	/**
	 * Về {@code ON_SALE} nếu đã qua giờ mở bán, ngược lại {@code PUBLISHED}. {@code ORGANIZER_SUSPENDED} khi organizer
	 * còn bị đình chỉ, {@code ORGANIZER_PAYOUT_NOT_READY} khi chưa {@code charges_enabled}.
	 */
	EventStatusResult resumeEvent(UUID adminId, UUID eventId);

	/** Đình chỉ organizer và tạm dừng mọi sự kiện {@code PUBLISHED} / {@code ON_SALE} của họ. */
	OrganizerSuspension suspendOrganizer(UUID adminId, UUID organizerId, String reason);

	/**
	 * Cập nhật {@code payout_ready}; mất {@code charges_enabled} thì tạm dừng tự động mọi sự kiện đang bán (BR-13).
	 * Bật lại không tự mở bán. Gửi lại cùng event không xử lý lại.
	 */
	void onPayoutStatusChanged(OrganizerPayoutStatusChanged event);

	/** Khôi phục organizer về {@code APPROVED}; sự kiện vẫn tạm dừng tới khi Admin mở lại từng cái. */
	OrganizerSuspension reinstateOrganizer(UUID adminId, UUID organizerId, String reason);

}
