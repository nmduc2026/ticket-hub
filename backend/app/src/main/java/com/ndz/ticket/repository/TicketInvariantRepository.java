package com.ndz.ticket.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy vấn kiểm tra bất biến của vé (overview.md §10, §16.3). */
@Repository
public class TicketInvariantRepository {

	private final JdbcTemplate jdbc;

	TicketInvariantRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Số ghế có nhiều hơn một vé chưa thu hồi: truy vấn ghế trùng của §10. Luôn bằng 0 khi {@code uq_ticket_seat_active}
	 * còn nguyên; khác 0 nghĩa là chốt chặn cuối đã mất (alert mức cao nhất).
	 */
	public int countSeatsWithMoreThanOneLiveTicket() {
		Integer count = jdbc.queryForObject("""
				SELECT count(*) FROM (
				    SELECT event_id, seat_id FROM ticket.tickets
				    WHERE status <> 'REVOKED' GROUP BY event_id, seat_id HAVING count(*) > 1) duplicates
				""", Integer.class);
		return count == null ? 0 : count;
	}

}
