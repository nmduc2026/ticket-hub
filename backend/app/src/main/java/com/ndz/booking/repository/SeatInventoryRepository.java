package com.ndz.booking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.catalog.api.CatalogApi.SellableSeat;

/** Tồn kho ghế theo sự kiện (overview.md §6.2). */
@Repository
public class SeatInventoryRepository {

	private static final int BATCH_SIZE = 500;

	private final JdbcTemplate jdbc;

	SeatInventoryRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Tạo tồn kho ghế, idempotent: ghế đã có (kể cả đang giữ hoặc đã bán) được giữ nguyên.
	 *
	 * @return số dòng thật sự được thêm
	 */
	public int insertAll(UUID eventId, List<SellableSeat> seats) {
		int before = count(eventId);
		jdbc.batchUpdate("""
				INSERT INTO booking.seat_inventory
				    (event_id, seat_id, tier_id, tier_name, section_code, row_label, seat_number, price_minor, currency)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (event_id, seat_id) DO NOTHING
				""", seats, BATCH_SIZE, (statement, seat) -> {
			statement.setObject(1, eventId);
			statement.setObject(2, seat.seatId());
			statement.setObject(3, seat.tierId());
			statement.setString(4, seat.tierName());
			statement.setString(5, seat.sectionCode());
			statement.setString(6, seat.rowLabel());
			statement.setInt(7, seat.seatNumber());
			statement.setLong(8, seat.priceMinor());
			statement.setString(9, seat.currency());
		});
		return count(eventId) - before;
	}

	public int count(UUID eventId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM booking.seat_inventory WHERE event_id = ?",
				Integer.class, eventId);
		return count == null ? 0 : count;
	}

}
