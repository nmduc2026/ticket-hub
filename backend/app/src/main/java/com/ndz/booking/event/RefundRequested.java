package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Booking đã chuyển sang {@code REFUND_REQUESTED} (overview.md §7.4): Ticket thu hồi mọi vé của booking ngay (BR-09),
 * Payment tạo bản ghi hoàn và gọi Stripe, Notification báo cho khách. {@code reason} quyết định số tiền hoàn: lý do
 * khách yêu cầu hoàn trừ phí nền tảng, các lý do còn lại hoàn 100% (BR-07, BR-08).
 */
public record RefundRequested(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String eventTitle, String contactEmail, String reason, long refundAmountMinor, String currency)
		implements IntegrationEvent {

	/** Khách yêu cầu hoàn (BR-07): hoàn tổng đã trả trừ phí nền tảng. */
	public static final String CUSTOMER_REQUEST = "CUSTOMER_REQUEST";
	/** Phát vé vi phạm UNIQUE (§7.3): hoàn 100%. */
	public static final String TICKET_ISSUANCE_FAILED = "TICKET_ISSUANCE_FAILED";

}
