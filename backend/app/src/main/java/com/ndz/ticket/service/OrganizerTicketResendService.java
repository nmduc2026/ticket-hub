package com.ndz.ticket.service;

import java.util.List;
import java.util.UUID;

import com.ndz.ticket.dto.response.ResendResponses.BulkResult;

/** Gửi lại email vé cho các đơn đã chọn (#27, overview.md §11.1). */
public interface OrganizerTicketResendService {

	/**
	 * Mỗi id xử lý riêng, không ảnh hưởng id khác. Bỏ qua kèm lý do khi: đơn không tồn tại hoặc không phải của
	 * organizer gọi ({@code NOT_EVENT_OWNER}), đơn chưa {@code CONFIRMED} ({@code BOOKING_NOT_CONFIRMED}), hoặc vé đã
	 * bị thu hồi hết ({@code TICKET_REVOKED}).
	 */
	BulkResult resend(UUID organizerId, List<UUID> bookingIds);

}
