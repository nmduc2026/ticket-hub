package com.ndz.booking.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ghi {@code sales_by_event} (overview.md §6.2). Gọi trong transaction của chuyển trạng thái booking nên mỗi booking
 * chỉ được cộng một lần. Phí theo hạng = floor(tiền hạng × platform_fee_bps / 10000) (sum là numeric chính xác, không float; BR-10), có thể
 * lệch Payment ≤ 1đ.
 */
@Repository
public class SalesRepository {

	private static final String ADD_SALE = """
			INSERT INTO booking.sales_by_event (event_id, tier_id, sold_count, revenue_minor, fee_minor, updated_at)
			SELECT i.event_id, i.tier_id, count(*), sum(i.price_minor), floor(sum(i.price_minor) * e.platform_fee_bps / 10000), ?
			FROM booking.booking_items i JOIN booking.event_snapshots e ON e.event_id = i.event_id
			WHERE i.booking_id = ?
			GROUP BY i.event_id, i.tier_id, e.platform_fee_bps
			ON CONFLICT (event_id, tier_id) DO UPDATE SET
			    sold_count = sales_by_event.sold_count + EXCLUDED.sold_count,
			    revenue_minor = sales_by_event.revenue_minor + EXCLUDED.revenue_minor,
			    fee_minor = sales_by_event.fee_minor + EXCLUDED.fee_minor,
			    updated_at = EXCLUDED.updated_at
			""";

	private static final String ADD_REFUND = """
			INSERT INTO booking.sales_by_event (event_id, tier_id, refunded_count, refunded_minor, refunded_fee_minor,
			                                    updated_at)
			SELECT i.event_id, i.tier_id, count(*), sum(i.price_minor), floor(sum(i.price_minor) * e.platform_fee_bps / 10000), ?
			FROM booking.booking_items i JOIN booking.event_snapshots e ON e.event_id = i.event_id
			WHERE i.booking_id = ?
			GROUP BY i.event_id, i.tier_id, e.platform_fee_bps
			ON CONFLICT (event_id, tier_id) DO UPDATE SET
			    refunded_count = sales_by_event.refunded_count + EXCLUDED.refunded_count,
			    refunded_minor = sales_by_event.refunded_minor + EXCLUDED.refunded_minor,
			    refunded_fee_minor = sales_by_event.refunded_fee_minor + EXCLUDED.refunded_fee_minor,
			    updated_at = EXCLUDED.updated_at
			""";

	private static final String ADD_CHECK_IN = """
			INSERT INTO booking.sales_by_event (event_id, tier_id, checked_in_count, updated_at)
			SELECT s.event_id, s.tier_id, 1, ? FROM booking.seat_inventory s WHERE s.event_id = ? AND s.seat_id = ?
			ON CONFLICT (event_id, tier_id) DO UPDATE SET
			    checked_in_count = sales_by_event.checked_in_count + 1, updated_at = EXCLUDED.updated_at
			""";

	private final JdbcTemplate jdbc;

	SalesRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Booking vừa {@code CONFIRMED}: cộng số vé, tiền gộp và phí theo hạng; ghi mốc {@code confirmed_at}. */
	public void recordConfirmed(UUID bookingId, Instant now) {
		jdbc.update(ADD_SALE, Timestamp.from(now), bookingId);
		jdbc.update("UPDATE booking.bookings SET confirmed_at = ? WHERE id = ? AND confirmed_at IS NULL",
				Timestamp.from(now), bookingId);
	}

	/** Booking vừa {@code REFUNDED}: cộng phần hoàn theo giá gốc (không phải số khách nhận lại). */
	public void recordRefunded(UUID bookingId, Instant now) {
		jdbc.update(ADD_REFUND, Timestamp.from(now), bookingId);
	}

	/** Một vé vừa check-in; hạng vé tra theo ghế trong {@code seat_inventory}. */
	public int recordCheckIn(UUID eventId, UUID seatId, Instant now) {
		return jdbc.update(ADD_CHECK_IN, Timestamp.from(now), eventId, seatId);
	}

}
