package com.ndz.ticket.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Booking đã yêu cầu hoàn mà Ticket chưa có vé nào để thu hồi (BookingConfirmed đến sau RefundRequested). Vé phát sau
 * đó phải ra đời đã bị thu hồi (BR-09).
 */
@Repository
public class RevokedBookingRepository {

	private final JdbcTemplate jdbc;

	RevokedBookingRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void mark(UUID bookingId, Instant now) {
		jdbc.update("INSERT INTO ticket.revoked_bookings (booking_id, revoked_at) VALUES (?, ?) "
				+ "ON CONFLICT (booking_id) DO NOTHING", bookingId, Timestamp.from(now));
	}

	public boolean isMarked(UUID bookingId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM ticket.revoked_bookings WHERE booking_id = ?)", Boolean.class, bookingId));
	}

}
