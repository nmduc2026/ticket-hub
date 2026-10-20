package com.ndz.backend.booking.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.backend.booking.internal.domain.BookingViews.EventRules;
import com.ndz.backend.catalog.events.EventPublished;
import com.ndz.backend.catalog.events.EventUpdated;

/**
 * Bản sao thông tin sự kiện của Booking (overview.md §6.2). Mọi cập nhật có điều kiện {@code last_event_at < occurredAt}
 * nên event đến sai thứ tự hoặc gửi lại không ghi đè dữ liệu mới hơn.
 */
@Repository
public class EventSnapshotRepository {

	private final JdbcTemplate jdbc;

	EventSnapshotRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Tạo bản sao khi sự kiện được publish; nếu đã có (event gửi lại) thì chỉ ghi khi event mới hơn. */
	public boolean upsertPublished(EventPublished event) {
		return jdbc.update("""
				INSERT INTO booking.event_snapshots
				    (event_id, organizer_id, title, description, category, banner_url, venue_id, venue_name, city_id, city_name,
				     seat_map_id, starts_at, ends_at, sale_starts_at, status, high_demand, max_tickets_per_user,
				     platform_fee_bps, last_event_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)
				ON CONFLICT (event_id) DO UPDATE SET
				    organizer_id = EXCLUDED.organizer_id, title = EXCLUDED.title, description = EXCLUDED.description,
				    category = EXCLUDED.category, banner_url = EXCLUDED.banner_url, venue_id = EXCLUDED.venue_id,
				    venue_name = EXCLUDED.venue_name, city_id = EXCLUDED.city_id, city_name = EXCLUDED.city_name, seat_map_id = EXCLUDED.seat_map_id,
				    starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at,
				    sale_starts_at = EXCLUDED.sale_starts_at, status = 'PUBLISHED', high_demand = EXCLUDED.high_demand,
				    max_tickets_per_user = EXCLUDED.max_tickets_per_user, platform_fee_bps = EXCLUDED.platform_fee_bps,
				    last_event_at = EXCLUDED.last_event_at, version = booking.event_snapshots.version + 1
				WHERE booking.event_snapshots.last_event_at < EXCLUDED.last_event_at
				""", event.eventId(), event.organizerId(), event.title(), event.description(), event.category(),
				event.bannerUrl(), event.venueId(), event.venueName(), event.cityId(), event.cityName(), event.seatMapId(),
				Timestamp.from(event.startsAt()), Timestamp.from(event.endsAt()),
				Timestamp.from(event.saleStartsAt()), event.highDemand(), event.maxTicketsPerUser(),
				event.platformFeeBps(), Timestamp.from(event.occurredAt())) == 1;
	}

	/** @return true nếu đã áp dụng; false nếu event cũ hơn dữ liệu đang có hoặc chưa có bản sao */
	public boolean updateDisplay(EventUpdated event) {
		return jdbc.update("""
				UPDATE booking.event_snapshots
				SET title = ?, description = ?, category = ?, banner_url = ?, last_event_at = ?, version = version + 1
				WHERE event_id = ? AND last_event_at < ?
				""", event.title(), event.description(), event.category(), event.bannerUrl(),
				Timestamp.from(event.occurredAt()), event.eventId(), Timestamp.from(event.occurredAt())) == 1;
	}

	/** @return true nếu đã áp dụng; false nếu event cũ hơn dữ liệu đang có hoặc chưa có bản sao */
	public boolean updateStatus(UUID eventId, String status, Instant occurredAt) {
		return jdbc.update("""
				UPDATE booking.event_snapshots SET status = ?, last_event_at = ?, version = version + 1
				WHERE event_id = ? AND last_event_at < ?
				""", status, Timestamp.from(occurredAt), eventId, Timestamp.from(occurredAt)) == 1;
	}

	/** Quy tắc đặt vé của sự kiện (trạng thái, giới hạn vé mỗi người), đọc từ bản sao; không đọc DB của Catalog. */
	public Optional<EventRules> rules(UUID eventId) {
		return jdbc.query(
				"SELECT event_id, status, max_tickets_per_user, sale_starts_at FROM booking.event_snapshots WHERE event_id = ?",
				(rs, i) -> new EventRules(rs.getObject("event_id", UUID.class), rs.getString("status"),
						rs.getInt("max_tickets_per_user"), rs.getTimestamp("sale_starts_at").toInstant()),
				eventId).stream().findFirst();
	}

	public boolean exists(UUID eventId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM booking.event_snapshots WHERE event_id = ?)", Boolean.class, eventId));
	}

}
