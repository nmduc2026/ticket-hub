package com.ndz.catalog.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.catalog.dto.response.CatalogViews.SeatMapRow;
import com.ndz.catalog.util.SeatGenerator.GeneratedSeat;
import com.ndz.shared.util.Uuid7;

/**
 * Ghi sơ đồ ghế bằng SQL (ADR 0030): khu, phần tử không bán và ghế sinh từ tham số khu. Ghế lưu theo lô nên dùng
 * JDBC thay vì entity. Đọc dùng {@link CatalogReadRepository}.
 */
@Repository
public class SeatMapAdminRepository {

	private final JdbcTemplate jdbc;
	private final java.time.Clock clock;

	SeatMapAdminRepository(JdbcTemplate jdbc, java.time.Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record Summary(SeatMapRow map, int sectionCount, int seatCount) {
	}

	public record StoredSection(UUID id, String code, String name, String shapeJson) {
	}

	public List<Summary> listAll(UUID venueId) {
		return jdbc.query("""
				SELECT m.id, m.venue_id, m.name, m.version, m.width, m.height, m.locked,
				       (SELECT count(*) FROM catalog.sections s WHERE s.seat_map_id = m.id) AS sections,
				       (SELECT count(*) FROM catalog.seats t JOIN catalog.sections s ON s.id = t.section_id
				        WHERE s.seat_map_id = m.id) AS seats
				FROM catalog.seat_maps m WHERE (?::uuid IS NULL OR m.venue_id = ?) ORDER BY m.name, m.version
				""", (rs, i) -> new Summary(mapRow(rs), rs.getInt("sections"), rs.getInt("seats")), venueId, venueId);
	}

	public Optional<SeatMapRow> find(UUID id) {
		return jdbc.query("""
				SELECT id, venue_id, name, version, width, height, locked FROM catalog.seat_maps WHERE id = ?
				""", (rs, i) -> mapRow(rs), id).stream().findFirst();
	}

	private static SeatMapRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new SeatMapRow(rs.getObject("id", UUID.class), rs.getObject("venue_id", UUID.class), rs.getString("name"),
				rs.getInt("version"), rs.getInt("width"), rs.getInt("height"), rs.getBoolean("locked"));
	}

	public boolean venueExists(UUID venueId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM catalog.venues WHERE id = ?)",
				Boolean.class, venueId));
	}

	public int nextVersion(UUID venueId, String name) {
		return jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM catalog.seat_maps WHERE venue_id = ? AND name = ?",
				Integer.class, venueId, name);
	}

	public void insertMap(UUID id, UUID venueId, String name, int version, int width, int height) {
		jdbc.update("INSERT INTO catalog.seat_maps (id, venue_id, name, version, width, height) VALUES (?, ?, ?, ?, ?, ?)",
				id, venueId, name, version, width, height);
	}

	public void updateMap(UUID id, String name, int width, int height) {
		jdbc.update("UPDATE catalog.seat_maps SET name = ?, width = ?, height = ? WHERE id = ?", name, width, height, id);
	}

	public boolean usedByEvent(UUID seatMapId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM catalog.events WHERE seat_map_id = ?)", Boolean.class, seatMapId));
	}

	public Map<String, StoredSection> sectionsByCode(UUID seatMapId) {
		Map<String, StoredSection> byCode = new HashMap<>();
		jdbc.query("SELECT id, code, name, shape_json::text FROM catalog.sections WHERE seat_map_id = ?", rs -> {
			byCode.put(rs.getString(2), new StoredSection(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
					rs.getString(4)));
		}, seatMapId);
		return byCode;
	}

	public boolean sectionInUse(UUID sectionId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM catalog.tier_sections WHERE section_id = ?)", Boolean.class, sectionId));
	}

	public void insertSection(UUID id, UUID seatMapId, String code, String name, String shapeJson) {
		jdbc.update("INSERT INTO catalog.sections (id, seat_map_id, code, name, shape_json) VALUES (?, ?, ?, ?, ?::jsonb)",
				id, seatMapId, code, name, shapeJson);
	}

	public void updateSection(UUID id, String name, String shapeJson) {
		jdbc.update("UPDATE catalog.sections SET name = ?, shape_json = ?::jsonb WHERE id = ?", name, shapeJson, id);
	}

	public void deleteSection(UUID id) {
		jdbc.update("DELETE FROM catalog.seats WHERE section_id = ?", id);
		jdbc.update("DELETE FROM catalog.sections WHERE id = ?", id);
	}

	/** Thay toàn bộ ghế của khu; toạ độ lưu số nguyên (làm tròn). */
	public void replaceSeats(UUID sectionId, List<GeneratedSeat> seats) {
		jdbc.update("DELETE FROM catalog.seats WHERE section_id = ?", sectionId);
		insertSeats(sectionId, seats.stream().map(seat -> new SeatRow(seat.rowLabel(), seat.seatNumber(),
				(int) Math.round(seat.x()), (int) Math.round(seat.y()))).toList());
	}

	public record SeatRow(String rowLabel, int seatNumber, int x, int y) {
	}

	public List<SeatRow> seatsOf(UUID sectionId) {
		return jdbc.query("SELECT row_label, seat_number, x, y FROM catalog.seats WHERE section_id = ? ORDER BY row_label, seat_number",
				(rs, i) -> new SeatRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), sectionId);
	}

	public void insertSeats(UUID sectionId, List<SeatRow> seats) {
		jdbc.batchUpdate("INSERT INTO catalog.seats (id, section_id, row_label, seat_number, x, y) VALUES (?, ?, ?, ?, ?, ?)",
				seats, 1000, (ps, seat) -> {
					ps.setObject(1, Uuid7.next(clock));
					ps.setObject(2, sectionId);
					ps.setString(3, seat.rowLabel());
					ps.setInt(4, seat.seatNumber());
					ps.setInt(5, seat.x());
					ps.setInt(6, seat.y());
				});
	}

	public void replaceElements(UUID seatMapId, List<ElementRow> elements) {
		jdbc.update("DELETE FROM catalog.seat_map_elements WHERE seat_map_id = ?", seatMapId);
		jdbc.batchUpdate("INSERT INTO catalog.seat_map_elements (id, seat_map_id, type, label, shape_json) VALUES (?, ?, ?, ?, ?::jsonb)",
				elements, 500, (ps, element) -> {
					ps.setObject(1, Uuid7.next(clock));
					ps.setObject(2, seatMapId);
					ps.setString(3, element.type());
					ps.setString(4, element.label());
					ps.setString(5, element.shapeJson());
				});
	}

	public record ElementRow(String type, String label, String shapeJson) {
	}

	public void deleteMap(UUID id) {
		jdbc.update("DELETE FROM catalog.seats WHERE section_id IN (SELECT id FROM catalog.sections WHERE seat_map_id = ?)", id);
		jdbc.update("DELETE FROM catalog.sections WHERE seat_map_id = ?", id);
		jdbc.update("DELETE FROM catalog.seat_map_elements WHERE seat_map_id = ?", id);
		jdbc.update("DELETE FROM catalog.seat_maps WHERE id = ?", id);
	}

}
