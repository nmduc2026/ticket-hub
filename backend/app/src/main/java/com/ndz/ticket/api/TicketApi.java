package com.ndz.ticket.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Interface công khai của module Ticket cho module khác gọi đồng bộ (ADR 0018). Khi tách service, thay cài đặt bằng
 * lời gọi API nội bộ; code gọi giữ nguyên.
 */
public interface TicketApi {

	/** Vé của một người dùng, mới phát trước, tối đa {@code limit} dòng (Admin xem chi tiết người dùng). Không kèm mã QR. */
	List<UserTicket> ticketsOf(UUID userId, int limit);

	record UserTicket(UUID ticketId, String eventTitle, Instant startsAt, String venueName, String sectionCode,
			String row, int number, String tierName, String status) {
	}

}
