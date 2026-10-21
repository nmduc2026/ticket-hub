package com.ndz.backend.booking.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.backend.booking.dto.response.EventViews.EventDetail;
import com.ndz.backend.booking.dto.response.EventViews.EventListItem;
import com.ndz.backend.booking.dto.response.EventViews.EventPage;
import com.ndz.backend.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.backend.booking.dto.response.EventViews.SeatState;
import com.ndz.backend.booking.dto.response.EventViews.TierAvailability;

/**
 * Truy vấn đọc cho API công khai về sự kiện (overview.md §11.2). Dùng {@code event_snapshots} và {@code seat_inventory}
 * của chính Booking, không đọc DB của Catalog. Mọi giá trị người dùng đi qua tham số; chuỗi tìm kiếm được escape.
 */
@Repository
public class PublicEventQueries {

	/** Sự kiện hiển thị ở danh sách: đang chờ mở bán, đang bán hoặc tạm dừng. Sự kiện đã kết thúc / huỷ ẩn khỏi danh sách. */
	private static final String LISTED = "e.status IN ('PUBLISHED', 'ON_SALE', 'SUSPENDED') AND e.ends_at > ?";

	private static final char ESCAPE = '!';

	private final JdbcTemplate jdbc;

	PublicEventQueries(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public EventPage search(SearchCriteria criteria, int page, int size, Instant now) {
		StringBuilder where = new StringBuilder(" WHERE " + LISTED);
		List<Object> args = new ArrayList<>();
		args.add(Timestamp.from(now));
		if (criteria.query() != null && !criteria.query().isBlank()) {
			where.append(" AND (e.title ILIKE ? ESCAPE '!' OR e.venue_name ILIKE ? ESCAPE '!')");
			String pattern = "%" + escapeLike(criteria.query().trim()) + "%";
			args.add(pattern);
			args.add(pattern);
		}
		if (criteria.cityId() != null) {
			where.append(" AND e.city_id = ?");
			args.add(criteria.cityId());
		}
		if (criteria.category() != null && !criteria.category().isBlank()) {
			where.append(" AND e.category = ?");
			args.add(criteria.category().trim());
		}
		if (criteria.from() != null) {
			where.append(" AND e.starts_at >= ?");
			args.add(Timestamp.from(criteria.from()));
		}
		if (criteria.to() != null) {
			where.append(" AND e.starts_at < ?");
			args.add(Timestamp.from(criteria.to()));
		}

		Long total = jdbc.queryForObject("SELECT count(*) FROM booking.event_snapshots e" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((long) page * size);
		List<EventListItem> items = jdbc.query("""
				SELECT e.event_id, e.title, e.banner_url, e.starts_at, e.venue_name, e.city_name, e.currency, e.status,
				       (SELECT min(i.price_minor) FROM booking.seat_inventory i WHERE i.event_id = e.event_id) AS min_price
				FROM booking.event_snapshots e
				""" + where + " ORDER BY e.starts_at, e.event_id LIMIT ? OFFSET ?", (rs, i) -> {
			Long minPrice = rs.getObject("min_price", Long.class);
			return new EventListItem(rs.getObject("event_id", UUID.class), rs.getString("title"),
					rs.getString("banner_url"), rs.getTimestamp("starts_at").toInstant(), rs.getString("venue_name"),
					rs.getString("city_name"), minPrice, rs.getString("currency"),
					rs.getString("status"));
		}, pageArgs.toArray());
		return new EventPage(items, total == null ? 0 : total, page, size);
	}

	public Optional<EventDetail> detail(UUID eventId) {
		return jdbc.query("""
				SELECT event_id, title, description, category, banner_url, organizer_id, venue_id, venue_name,
				       city_name, starts_at, ends_at, sale_starts_at, status, high_demand, max_tickets_per_user,
				       seat_map_id, currency
				FROM booking.event_snapshots WHERE event_id = ?
				""", (rs, i) -> new EventDetail(rs.getObject("event_id", UUID.class), rs.getString("title"),
				rs.getString("description"), rs.getString("category"), rs.getString("banner_url"),
				rs.getObject("organizer_id", UUID.class), rs.getObject("venue_id", UUID.class),
				rs.getString("venue_name"), rs.getString("city_name"), rs.getTimestamp("starts_at").toInstant(),
				rs.getTimestamp("ends_at").toInstant(), rs.getTimestamp("sale_starts_at").toInstant(),
				rs.getString("status"), rs.getBoolean("high_demand"), rs.getInt("max_tickets_per_user"),
				rs.getObject("seat_map_id", UUID.class), rs.getString("currency")), eventId).stream().findFirst();
	}

	public List<TierAvailability> tiers(UUID eventId) {
		return jdbc.query("""
				SELECT tier_id, tier_name, price_minor, currency, count(*) AS total,
				       count(*) FILTER (WHERE status = 'AVAILABLE') AS available
				FROM booking.seat_inventory WHERE event_id = ?
				GROUP BY tier_id, tier_name, price_minor, currency
				ORDER BY price_minor DESC, tier_name
				""", (rs, i) -> new TierAvailability(rs.getObject("tier_id", UUID.class), rs.getString("tier_name"),
				rs.getLong("price_minor"), rs.getString("currency"), rs.getInt("total"), rs.getInt("available")),
				eventId);
	}

	public Map<UUID, SeatState> seatStates(UUID eventId) {
		return jdbc.query("SELECT seat_id, tier_id, status FROM booking.seat_inventory WHERE event_id = ?",
				(rs, i) -> new SeatState(rs.getObject("seat_id", UUID.class), rs.getObject("tier_id", UUID.class),
						rs.getString("status")),
				eventId).stream().collect(Collectors.toMap(SeatState::seatId, state -> state));
	}

	/** Hạng vé của từng khu (theo mã khu), suy ra từ tồn kho: khu không có ghế trong tồn kho là khu không bán. */
	public Map<String, UUID> tierBySectionCode(UUID eventId) {
		Map<String, UUID> result = new java.util.HashMap<>();
		jdbc.query("SELECT DISTINCT section_code, tier_id FROM booking.seat_inventory WHERE event_id = ?",
				rs -> {
					result.put(rs.getString("section_code"), rs.getObject("tier_id", UUID.class));
				}, eventId);
		return result;
	}

	private static String escapeLike(String text) {
		StringBuilder escaped = new StringBuilder();
		for (char character : text.toCharArray()) {
			if (character == ESCAPE || character == '%' || character == '_') {
				escaped.append(ESCAPE);
			}
			escaped.append(character);
		}
		return escaped.toString();
	}

}
