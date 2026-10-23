package com.ndz.ticket.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Nhật ký check-in (overview.md §6.4): mỗi vé một dòng, UNIQUE (ticket_id) là chốt chặn thứ hai sau {@code markUsed}. */
@Repository
public class CheckInRepository {

	private final JdbcTemplate jdbc;

	CheckInRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(UUID id, UUID ticketId, String gate, UUID scannedBy, Instant scannedAt) {
		jdbc.update("INSERT INTO ticket.check_ins (id, ticket_id, gate, scanned_by, scanned_at) VALUES (?, ?, ?, ?, ?)",
				id, ticketId, gate, scannedBy, Timestamp.from(scannedAt));
	}

}
