package com.ndz.catalog.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Khử trùng consumer của Catalog (overview.md §8.3): ghi message đã xử lý trong cùng transaction với xử lý. */
@Repository
public class CatalogProcessedEventRepository {

	private final JdbcTemplate jdbc;

	CatalogProcessedEventRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return true nếu đây là lần đầu (cứ xử lý tiếp); false nếu đã xử lý trước đó (bỏ qua) */
	public boolean markProcessed(String consumer, UUID messageId) {
		return jdbc.update("""
				INSERT INTO catalog.processed_events (consumer, message_id) VALUES (?, ?)
				ON CONFLICT (consumer, message_id) DO NOTHING
				""", consumer, messageId) == 1;
	}

}
