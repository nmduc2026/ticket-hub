package com.ndz.booking.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.booking.dto.response.BookingViews.BookingItemView;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.dto.response.BookingViews.BookingView;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.LockedSeat;

/**
 * Đơn đặt vé và việc giữ ghế trong tồn kho (overview.md §10, lớp 2 — Postgres là nguồn sự thật). Các hàm khoá / cập
 * nhật ghế phải chạy trong transaction của use case; khoá theo thứ tự {@code seat_id} cố định để tránh deadlock.
 */
@Repository
public class BookingRepository {

	private final JdbcTemplate jdbc;

	BookingRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Khoá các ghế (theo thứ tự seat_id) và trả về trạng thái hiện tại; ghế không có trong tồn kho không được trả về. */
	public List<LockedSeat> lockSeats(UUID eventId, List<UUID> seatIds) {
		return jdbc.query(connection -> {
			var statement = connection.prepareStatement("""
					SELECT seat_id, tier_id, tier_name, section_code, row_label, seat_number, price_minor, currency,
					       status
					FROM booking.seat_inventory
					WHERE event_id = ? AND seat_id = ANY (?)
					ORDER BY seat_id
					FOR UPDATE
					""");
			statement.setObject(1, eventId);
			statement.setArray(2, connection.createArrayOf("uuid", seatIds.toArray()));
			return statement;
		}, (rs, i) -> new LockedSeat(rs.getObject("seat_id", UUID.class), rs.getObject("tier_id", UUID.class),
				rs.getString("tier_name"), rs.getString("section_code"), rs.getString("row_label"),
				rs.getInt("seat_number"), rs.getLong("price_minor"), rs.getString("currency"),
				rs.getString("status")));
	}

	public void insertBooking(UUID id, UUID userId, UUID eventId, long totalMinor, String currency, Instant expiresAt,
			String contactEmail, Instant now) {
		jdbc.update("""
				INSERT INTO booking.bookings (id, user_id, event_id, status, total_minor, currency, expires_at,
				                              contact_email, created_at)
				VALUES (?, ?, ?, 'PENDING_PAYMENT', ?, ?, ?, ?, ?)
				""", id, userId, eventId, totalMinor, currency, Timestamp.from(expiresAt), contactEmail,
				Timestamp.from(now));
	}

	public void insertItems(UUID bookingId, UUID eventId, List<LockedSeat> seats) {
		jdbc.batchUpdate("""
				INSERT INTO booking.booking_items (booking_id, event_id, seat_id, tier_id, price_minor)
				VALUES (?, ?, ?, ?, ?)
				""", seats, seats.size(), (statement, seat) -> {
			statement.setObject(1, bookingId);
			statement.setObject(2, eventId);
			statement.setObject(3, seat.seatId());
			statement.setObject(4, seat.tierId());
			statement.setLong(5, seat.priceMinor());
		});
	}

	/**
	 * Giữ ghế bằng cập nhật có điều kiện: chỉ ghế còn {@code AVAILABLE} được đổi. Người gọi so số dòng với số ghế yêu
	 * cầu và hoàn tác nếu khác (BR-01).
	 */
	public int markHeld(UUID eventId, List<UUID> seatIds, UUID bookingId) {
		return jdbc.update(connection -> {
			var statement = connection.prepareStatement("""
					UPDATE booking.seat_inventory SET status = 'HELD', booking_id = ?, version = version + 1
					WHERE event_id = ? AND seat_id = ANY (?) AND status = 'AVAILABLE'
					""");
			statement.setObject(1, bookingId);
			statement.setObject(2, eventId);
			statement.setArray(3, connection.createArrayOf("uuid", seatIds.toArray()));
			return statement;
		});
	}

	public boolean hasPendingBooking(UUID userId, UUID eventId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM booking.bookings
				               WHERE user_id = ? AND event_id = ? AND status = 'PENDING_PAYMENT')
				""", Boolean.class, userId, eventId));
	}

	/** Tổng số vé của user cho sự kiện: cộng mọi booking đang chờ thanh toán và đã xác nhận (BR-02). */
	public int activeTicketCount(UUID userId, UUID eventId) {
		Integer count = jdbc.queryForObject("""
				SELECT count(*) FROM booking.booking_items i JOIN booking.bookings b ON b.id = i.booking_id
				WHERE b.user_id = ? AND b.event_id = ? AND b.status IN ('PENDING_PAYMENT', 'CONFIRMED')
				""", Integer.class, userId, eventId);
		return count == null ? 0 : count;
	}

	/** Booking của chính người dùng (chống IDOR); người khác hoặc không tồn tại đều cho kết quả rỗng. */
	public Optional<BookingView> findOwned(UUID bookingId, UUID userId) {
		return jdbc.query("""
				SELECT id, event_id, status, total_minor, currency, expires_at
				FROM booking.bookings WHERE id = ? AND user_id = ?
				""", (rs, i) -> new BookingView(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
				rs.getString("status"), rs.getLong("total_minor"), rs.getString("currency"),
				rs.getTimestamp("expires_at").toInstant(), items(bookingId)), bookingId, userId).stream().findFirst();
	}

	/**
	 * Booking chờ thanh toán đã quá hạn giữ ghế: hạn là {@code expires_at}, hoặc {@code expires_at + ân hạn} nếu đã bắt
	 * đầu thanh toán (BR-03). Chỉ trả id, theo lô; use case kiểm lại dưới khoá trước khi đổi.
	 */
	public List<UUID> findDueForExpiry(Instant now, Duration paymentGrace, int limit) {
		return jdbc.queryForList("""
				SELECT id FROM booking.bookings
				WHERE status = 'PENDING_PAYMENT'
				  AND (CASE WHEN payment_started_at IS NULL THEN expires_at ELSE expires_at + ? * interval '1 second' END) <= ?
				ORDER BY expires_at LIMIT ?
				""", UUID.class, paymentGrace.toSeconds(), Timestamp.from(now), limit);
	}

	/** Khoá dòng booking để chuyển trạng thái; người khác chờ tới khi transaction này kết thúc. */
	public Optional<LockedBooking> lockBooking(UUID bookingId) {
		return jdbc.query("""
				SELECT id, user_id, event_id, status, expires_at, payment_started_at
				FROM booking.bookings WHERE id = ? FOR UPDATE
				""", (rs, i) -> new LockedBooking(rs.getObject("id", UUID.class),
				rs.getObject("user_id", UUID.class), rs.getObject("event_id", UUID.class),
				BookingStatus.valueOf(rs.getString("status")), rs.getTimestamp("expires_at").toInstant(),
				rs.getTimestamp("payment_started_at") == null ? null
						: rs.getTimestamp("payment_started_at").toInstant()), bookingId).stream().findFirst();
	}

	/** Chuyển trạng thái có điều kiện trạng thái cũ; trả false nếu booking đã bị tiến trình khác chuyển. */
	public boolean transition(UUID bookingId, BookingStatus from, BookingStatus to) {
		return jdbc.update("UPDATE booking.bookings SET status = ?, version = version + 1 WHERE id = ? AND status = ?",
				to.name(), bookingId, from.name()) == 1;
	}

	/** Nhả các ghế đang giữ của booking về {@code AVAILABLE}; trả về id các ghế đã nhả. */
	public List<UUID> releaseSeats(UUID bookingId) {
		return jdbc.queryForList("""
				UPDATE booking.seat_inventory SET status = 'AVAILABLE', booking_id = NULL, version = version + 1
				WHERE booking_id = ? AND status = 'HELD'
				RETURNING seat_id
				""", UUID.class, bookingId);
	}

	private List<BookingItemView> items(UUID bookingId) {
		return jdbc.query("""
				SELECT i.seat_id, s.section_code, s.row_label, s.seat_number, i.tier_id, s.tier_name, i.price_minor
				FROM booking.booking_items i
				         JOIN booking.seat_inventory s ON s.event_id = i.event_id AND s.seat_id = i.seat_id
				WHERE i.booking_id = ? ORDER BY s.section_code, s.row_label, s.seat_number
				""", (rs, i) -> new BookingItemView(rs.getObject("seat_id", UUID.class), rs.getString("section_code"),
				rs.getString("row_label"), rs.getInt("seat_number"), rs.getObject("tier_id", UUID.class),
				rs.getString("tier_name"), rs.getLong("price_minor")), bookingId);
	}

}
