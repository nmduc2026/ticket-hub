package com.ndz.backend.catalog.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.backend.catalog.internal.domain.CatalogViews.ElementRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.OrganizerInfo;
import com.ndz.backend.catalog.internal.domain.CatalogViews.SeatMapRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.SectionRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.VenueInfo;
import com.ndz.backend.catalog.internal.domain.CatalogViews.VenueRow;

/**
 * Truy vấn đọc cho địa điểm, sơ đồ ghế, tổ chức và mức phí (dữ liệu do Admin quản lý, organizer chỉ chọn). Dùng SQL
 * thuần vì cột {@code shape_json} là JSONB trả nguyên văn cho frontend vẽ sơ đồ.
 */
@Repository
public class CatalogReadRepository {

	private final JdbcTemplate jdbc;

	CatalogReadRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<VenueRow> listVenues() {
		return jdbc.query("""
				SELECT v.id, v.name, v.address, w.name AS ward_name, c.name AS city_name, v.capacity
				FROM catalog.venues v
				         JOIN catalog.administrative_units w ON w.id = v.ward_id
				         JOIN catalog.administrative_units c ON c.id = w.parent_id
				ORDER BY v.name
				""", (rs, i) -> new VenueRow(rs.getObject("id", UUID.class), rs.getString("name"),
				rs.getString("address"), rs.getString("ward_name"), rs.getString("city_name"), rs.getInt("capacity")));
	}

	public boolean venueExists(UUID venueId) {
		return exists("SELECT EXISTS (SELECT 1 FROM catalog.venues WHERE id = ?)", venueId);
	}

	public boolean seatMapBelongsToVenue(UUID seatMapId, UUID venueId) {
		return exists("SELECT EXISTS (SELECT 1 FROM catalog.seat_maps WHERE id = ? AND venue_id = ?)", seatMapId,
				venueId);
	}

	public boolean organizerExists(UUID organizerId) {
		return exists("SELECT EXISTS (SELECT 1 FROM catalog.organizers WHERE id = ?)", organizerId);
	}

	public Optional<OrganizerInfo> organizer(UUID organizerId) {
		return jdbc.query("SELECT status, payout_ready FROM catalog.organizers WHERE id = ?",
				(rs, i) -> new OrganizerInfo(rs.getString("status"), rs.getBoolean("payout_ready")), organizerId)
				.stream().findFirst();
	}

	/** Mức phí nền tảng đang hiệu lực tại thời điểm {@code at} (BR-06): bản ghi mới nhất đã tới ngày áp dụng. */
	public Optional<Integer> feeBpsAt(Instant at) {
		return jdbc.queryForList("""
				SELECT fee_bps FROM catalog.platform_fee_policies
				WHERE effective_from <= ? ORDER BY effective_from DESC LIMIT 1
				""", Integer.class, Timestamp.from(at)).stream().findFirst();
	}

	public Optional<VenueInfo> venueInfo(UUID venueId) {
		return jdbc.query("""
				SELECT v.name, c.name AS city_name FROM catalog.venues v
				         JOIN catalog.administrative_units w ON w.id = v.ward_id
				         JOIN catalog.administrative_units c ON c.id = w.parent_id
				WHERE v.id = ?
				""", (rs, i) -> new VenueInfo(rs.getString("name"), rs.getString("city_name")), venueId).stream()
				.findFirst();
	}

	/** Khoá sơ đồ khi có sự kiện publish dùng (bất biến, overview.md §6.1). */
	public void lockSeatMap(UUID seatMapId) {
		jdbc.update("UPDATE catalog.seat_maps SET locked = TRUE WHERE id = ?", seatMapId);
	}

	public List<SeatMapRow> listSeatMaps(UUID venueId) {
		return jdbc.query("""
				SELECT id, venue_id, name, version, width, height, locked
				FROM catalog.seat_maps WHERE venue_id = ? ORDER BY name, version
				""", (rs, i) -> new SeatMapRow(rs.getObject("id", UUID.class), rs.getObject("venue_id", UUID.class),
				rs.getString("name"), rs.getInt("version"), rs.getInt("width"), rs.getInt("height"),
				rs.getBoolean("locked")), venueId);
	}

	public List<SectionRow> listSections(Collection<UUID> seatMapIds) {
		if (seatMapIds.isEmpty()) {
			return List.of();
		}
		String placeholders = String.join(",", seatMapIds.stream().map(id -> "?").toList());
		return jdbc.query("""
				SELECT s.id, s.seat_map_id, s.code, s.name, s.shape_json::text AS shape_json,
				       (SELECT count(*) FROM catalog.seats t WHERE t.section_id = s.id) AS seat_count
				FROM catalog.sections s WHERE s.seat_map_id IN (%s) ORDER BY s.code
				""".formatted(placeholders), (rs, i) -> new SectionRow(rs.getObject("id", UUID.class),
				rs.getObject("seat_map_id", UUID.class), rs.getString("code"), rs.getString("name"),
				rs.getString("shape_json"), rs.getInt("seat_count")), seatMapIds.toArray());
	}

	public List<ElementRow> listElements(Collection<UUID> seatMapIds) {
		if (seatMapIds.isEmpty()) {
			return List.of();
		}
		String placeholders = String.join(",", seatMapIds.stream().map(id -> "?").toList());
		return jdbc.query("""
				SELECT id, seat_map_id, type, label, shape_json::text AS shape_json
				FROM catalog.seat_map_elements WHERE seat_map_id IN (%s) ORDER BY type, id
				""".formatted(placeholders), (rs, i) -> new ElementRow(rs.getObject("id", UUID.class),
				rs.getObject("seat_map_id", UUID.class), rs.getString("type"), rs.getString("label"),
				rs.getString("shape_json")), seatMapIds.toArray());
	}

	public Set<UUID> sectionIdsOfSeatMap(UUID seatMapId) {
		return new HashSet<>(
				jdbc.queryForList("SELECT id FROM catalog.sections WHERE seat_map_id = ?", UUID.class, seatMapId));
	}

	/** Tên địa điểm theo id, để hiển thị cùng danh sách sự kiện mà không phải tải cả địa điểm. */
	public Map<UUID, String> venueNames(Collection<UUID> venueIds) {
		if (venueIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", venueIds.stream().map(id -> "?").toList());
		Map<UUID, String> names = new HashMap<>();
		jdbc.query("SELECT id, name FROM catalog.venues WHERE id IN (%s)".formatted(placeholders),
				rs -> {
					names.put(rs.getObject("id", UUID.class), rs.getString("name"));
				}, venueIds.toArray());
		return names;
	}

	private boolean exists(String sql, Object... args) {
		return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
	}

}
