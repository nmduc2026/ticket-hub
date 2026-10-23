package com.ndz.ticket.service;

import java.util.List;
import java.util.UUID;

import com.ndz.ticket.dto.response.TicketResponses.TicketResponse;

/** Đọc vé của chính người dùng (overview.md §11.2). */
public interface TicketQueryService {

	/** Vé của {@code userId}, mới phát trước; không bao giờ trả vé của người khác (chống IDOR). */
	List<TicketResponse> myTickets(UUID userId, int page, int size);

}
