package com.ndz.booking.service;

import java.util.UUID;

import com.ndz.booking.dto.response.BookingResponses.RefundProgress;
import com.ndz.catalog.event.EventCancelled;

/** Sự kiện bị huỷ (BR-08, overview.md §7.4 "Sự kiện bị huỷ"). */
public interface EventCancellationService {

	/**
	 * Bản sao sang {@code CANCELLED}; đơn {@code PENDING_PAYMENT} → {@code CANCELLED} (nhả ghế); đơn {@code CONFIRMED}
	 * → {@code REFUND_REQUESTED} hoàn 100%. Đơn đã / đang hoàn theo yêu cầu của khách giữ nguyên. Gửi lại event không
	 * xử lý lại.
	 */
	void onEventCancelled(EventCancelled event);

	/** Tiến độ hoàn tiền của sự kiện bị huỷ (màn #34). */
	RefundProgress refundProgress(UUID eventId);

}
