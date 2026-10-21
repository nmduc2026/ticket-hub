package com.ndz.ticket.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Khử trùng consumer (overview.md §8.3): ghi message đã xử lý trong cùng transaction với xử lý nghiệp vụ. */
@Repository
public class TicketProcessedEventRepository {

	private final JdbcTemplate jdbc;

	TicketProcessedEventRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Đánh dấu {@code (consumer, messageId)} là đã xử lý.
	 *
	 * @return true nếu đây là lần đầu (cứ xử lý tiếp); false nếu đã xử lý trước đó (bỏ qua)
	 */
	public boolean markProcessed(String consumer, UUID messageId) {
		return jdbc.update("""
				INSERT INTO ticket.processed_events (consumer, message_id) VALUES (?, ?)
				ON CONFLICT (consumer, message_id) DO NOTHING
				""", consumer, messageId) == 1;
	}

}
