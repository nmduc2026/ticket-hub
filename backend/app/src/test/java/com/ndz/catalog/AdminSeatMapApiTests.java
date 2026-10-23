package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * CRUD sơ đồ ghế của Admin (plan Sprint 6 hạng mục 17, ADR 0030; overview.md §6.1, §9): lưu thì sinh lại ghế từ tham
 * số khu; sơ đồ đã khoá không sửa được, sửa bằng bản sao {@code version} mới; hai Admin lưu cùng lúc thì một bên bị chặn.
 */
@IntegrationTest
class AdminSeatMapApiTests {

	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String NAME = "Sơ đồ thử Sprint 6";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	private final JsonMapper json = new JsonMapper();

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbc.update("delete from catalog.tier_sections where section_id in (select s.id from catalog.sections s "
				+ "join catalog.seat_maps m on m.id = s.seat_map_id where m.name = ?)", NAME);
		jdbc.update("delete from catalog.seats where section_id in (select s.id from catalog.sections s "
				+ "join catalog.seat_maps m on m.id = s.seat_map_id where m.name = ?)", NAME);
		jdbc.update("delete from catalog.sections where seat_map_id in (select id from catalog.seat_maps where name = ?)", NAME);
		jdbc.update("delete from catalog.seat_map_elements where seat_map_id in (select id from catalog.seat_maps where name = ?)", NAME);
		jdbc.update("delete from catalog.seat_maps where name = ?", NAME);
	}

	@Test
	@DisplayName("Lưu sơ đồ sinh ghế từ tham số khu: lưới 2 x 3 có 1 ghế ẩn → 5 ghế, toạ độ làm tròn")
	void saveGeneratesSeats() throws Exception {
		UUID id = create(body(grid("A", 2, 3, "[\"A2\"]"), 1));

		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id where c.seat_map_id = ?", id))
				.isEqualTo(5);
		assertThat(jdbc.queryForObject("""
				select x || ',' || y from catalog.seats s join catalog.sections c on c.id = s.section_id
				where c.seat_map_id = ? and s.row_label = 'B' and s.seat_number = 2
				""", String.class, id)).isEqualTo("124,80");
		call(get("/api/v1/admin/seat-maps/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.sections[0].seatCount").value(5))
				.andExpect(jsonPath("$.elements[0].type").value("STAGE")).andExpect(jsonPath("$.version").value(1));
	}

	@Test
	@DisplayName("Sửa sơ đồ đã khoá → 409 SEAT_MAP_LOCKED; không phải Admin → 403")
	void lockedMapCannotBeEdited() throws Exception {
		UUID id = create(body(grid("A", 2, 3, "[]"), 1));
		jdbc.update("update catalog.seat_maps set locked = true where id = ?", id);

		call(put("/api/v1/admin/seat-maps/" + id).contentType(MediaType.APPLICATION_JSON).content(body(grid("A", 3, 3, "[]"), 1)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_MAP_LOCKED"));
		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id where c.seat_map_id = ?", id))
				.isEqualTo(6);
		mockMvc.perform(get("/api/v1/admin/seat-maps").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer())).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Hai Admin lưu cùng lúc: bên giữ khoá lock:seatmap:{id} thắng, bên kia 409 SEAT_MAP_BEING_EDITED")
	void concurrentSaveIsRejected() throws Exception {
		UUID id = create(body(grid("A", 2, 3, "[]"), 1));
		redis.opsForValue().set("lock:seatmap:" + id, "admin-khac");
		try {
			call(put("/api/v1/admin/seat-maps/" + id).contentType(MediaType.APPLICATION_JSON).content(body(grid("A", 3, 3, "[]"), 1)))
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_MAP_BEING_EDITED"));
		}
		finally {
			redis.delete("lock:seatmap:" + id);
		}
		call(put("/api/v1/admin/seat-maps/" + id).contentType(MediaType.APPLICATION_JSON).content(body(grid("A", 3, 3, "[]"), 1)))
				.andExpect(status().isOk());
		assertThat(redis.hasKey("lock:seatmap:" + id)).isFalse();
	}

	@Test
	@DisplayName("Bản sao có version mới, chưa khoá, đủ ghế; bản gốc giữ nguyên và vẫn khoá")
	void copyCreatesNewVersion() throws Exception {
		UUID id = create(body(grid("A", 2, 3, "[]"), 1));
		jdbc.update("update catalog.seat_maps set locked = true where id = ?", id);

		String copy = call(post("/api/v1/admin/seat-maps/" + id + "/copy")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.locked").value(false))
				.andExpect(jsonPath("$.sections[0].seatCount").value(6)).andReturn().getResponse().getContentAsString();
		UUID copyId = UUID.fromString(json.readTree(copy).get("id").asString());

		assertThat(copyId).isNotEqualTo(id);
		assertThat(jdbc.queryForObject("select locked from catalog.seat_maps where id = ?", Boolean.class, id)).isTrue();
		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id where c.seat_map_id = ?", id))
				.isEqualTo(6);
		call(put("/api/v1/admin/seat-maps/" + copyId).contentType(MediaType.APPLICATION_JSON).content(body(grid("A", 3, 3, "[]"), 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sections[0].seatCount").value(9));
	}

	@Test
	@DisplayName("Xoá khu đã gán hạng vé → 409 SECTION_IN_USE; tham số khu sai → 422 SEAT_MAP_INVALID")
	void sectionInUseAndInvalidShape() throws Exception {
		UUID id = create(body(grid("A", 2, 3, "[]"), 1));
		UUID sectionId = jdbc.queryForObject("select id from catalog.sections where seat_map_id = ?", UUID.class, id);
		UUID eventId = UUID.randomUUID();
		UUID tierId = UUID.randomUUID();
		jdbc.update("""
				insert into catalog.events (id, organizer_id, venue_id, seat_map_id, title, status, inventory_ready)
				values (?, ?, ?::uuid, ?, 'Sự kiện gán khu', 'DRAFT', false)
				""", eventId, TestTokens.ORGANIZER_ID, VENUE, id);
		jdbc.update("insert into catalog.ticket_tiers (id, event_id, name, price_minor, currency) values (?, ?, 'Hạng 1', 100000, 'VND')",
				tierId, eventId);
		jdbc.update("insert into catalog.tier_sections (event_id, tier_id, section_id) values (?, ?, ?)", eventId, tierId, sectionId);
		try {
			call(put("/api/v1/admin/seat-maps/" + id).contentType(MediaType.APPLICATION_JSON).content(body(grid("B", 2, 3, "[]"), 1)))
					.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SECTION_IN_USE"));
			call(delete("/api/v1/admin/seat-maps/" + id)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("SEAT_MAP_IN_USE"));
		}
		finally {
			jdbc.update("delete from catalog.tier_sections where event_id = ?", eventId);
			jdbc.update("delete from catalog.ticket_tiers where event_id = ?", eventId);
			jdbc.update("delete from catalog.events where id = ?", eventId);
		}
		call(put("/api/v1/admin/seat-maps/" + id).contentType(MediaType.APPLICATION_JSON).content(body(grid("A", 0, 3, "[]"), 1)))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("SEAT_MAP_INVALID"));
	}

	// ---- helpers ----

	private UUID create(String body) throws Exception {
		String response = call(post("/api/v1/admin/seat-maps").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(json.readTree(response).get("id").asString());
	}

	private String body(String section, int elements) {
		String stage = elements > 0
				? "{\"type\":\"STAGE\",\"label\":\"Sân khấu\",\"shape\":{\"x\":50,\"y\":10,\"w\":300,\"h\":40,\"rotation\":0}}" : "";
		return "{\"venueId\":\"%s\",\"name\":\"%s\",\"width\":1000,\"height\":800,\"sections\":[%s],\"elements\":[%s]}"
				.formatted(VENUE, NAME, section, stage);
	}

	private static String grid(String code, int rows, int seatsPerRow, String hidden) {
		return ("{\"code\":\"%s\",\"name\":\"Khu %s\",\"shape\":{\"kind\":\"GRID\",\"x\":100,\"y\":50,\"rotation\":0,"
				+ "\"rows\":%d,\"seatsPerRow\":%d,\"rowGap\":30,\"seatGap\":24,\"curve\":0,\"numbering\":\"LTR\","
				+ "\"hiddenSeats\":%s}}").formatted(code, code, rows, seatsPerRow, hidden);
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(TestTokens.ADMIN_ID).roles("ADMIN").bearer()));
	}

}
