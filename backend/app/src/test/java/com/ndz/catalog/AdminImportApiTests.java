package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * Import Excel của Admin (plan Sprint 6 hạng mục 19, AC-16; docs/design/README.md §7): dry-run báo lỗi theo dòng–cột và
 * không ghi gì; import thật khớp theo mã, chạy lại không tạo trùng; dòng lỗi không chặn dòng hợp lệ.
 */
@IntegrationTest
class AdminImportApiTests {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	private static final String VENUE = "00000000-0000-7000-8000-0000000b0001";
	private static final String MAP_NAME = "Sơ đồ import thử";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbc.update("delete from catalog.venues where code like 'IMPT-%'");
		jdbc.update("delete from catalog.administrative_units where parent_id in "
				+ "(select id from catalog.administrative_units where code like 'IMPT-%')");
		jdbc.update("delete from catalog.administrative_units where code like 'IMPT-%'");
		jdbc.update("delete from catalog.seats where section_id in (select s.id from catalog.sections s "
				+ "join catalog.seat_maps m on m.id = s.seat_map_id where m.name = ?)", MAP_NAME);
		jdbc.update("delete from catalog.sections where seat_map_id in (select id from catalog.seat_maps where name = ?)", MAP_NAME);
		jdbc.update("delete from catalog.seat_maps where name = ?", MAP_NAME);
	}

	@Test
	@DisplayName("File mẫu của cả 3 loại tải được và import được nguyên trạng (README §7)")
	void templatesDownloadAndImport() throws Exception {
		for (String type : List.of("administrative-units", "venues", "seat-maps")) {
			mockMvc.perform(withAdmin(get("/api/v1/admin/imports/templates/" + type))).andExpect(status().isOk())
					.andExpect(content().contentType(XLSX));
		}
		byte[] units = mockMvc.perform(withAdmin(get("/api/v1/admin/imports/templates/administrative-units")))
				.andReturn().getResponse().getContentAsByteArray();
		// Mã mẫu đã có trong DB thật; dry-run chỉ để chứng minh file mẫu đọc được và hợp lệ.
		upload("administrative-units", units, "true", "UPDATE", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRows").value(2)).andExpect(jsonPath("$.errorRows").value(0))
				.andExpect(jsonPath("$.status").value("VALID"));
	}

	@Test
	@DisplayName("Đơn vị: dry-run báo lỗi theo dòng–cột và không ghi; import thật ghi dòng hợp lệ; chạy lại không trùng")
	void unitsDryRunThenImportThenReimport() throws Exception {
		byte[] file = workbook(List.of("ma", "ten", "loai", "ma_cha", "hieu_luc_tu"),
				List.of("IMPT-1", "Tỉnh thử", "Tỉnh", "", "2025-07-01"),
				List.of("IMPT-1-A", "Xã thử A", "Xã", "IMPT-1", ""),
				List.of("IMPT-1-B", "Xã thử B", "Xã", "KHONG-CO", ""),
				List.of("IMPT-1-C", "Xã thử C", "Làng", "IMPT-1", ""),
				List.of("IMPT-1-A", "Trùng mã", "Xã", "IMPT-1", ""));

		upload("administrative-units", file, "true", "UPDATE", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INVALID")).andExpect(jsonPath("$.totalRows").value(5))
				.andExpect(jsonPath("$.validRows").value(2)).andExpect(jsonPath("$.errorRows").value(3))
				.andExpect(jsonPath("$.created").value(2))
				.andExpect(jsonPath("$.errors[?(@.row == 4 && @.column == 'ma_cha')]").exists())
				.andExpect(jsonPath("$.errors[?(@.row == 5 && @.column == 'loai')]").exists())
				.andExpect(jsonPath("$.errors[?(@.row == 6 && @.column == 'ma')]").exists());
		assertThat(count("select count(*) from catalog.administrative_units where code like 'IMPT-%'")).isZero();

		String importId = upload("administrative-units", file, "false", "UPDATE", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IMPORTED")).andExpect(jsonPath("$.created").value(2))
				.andExpect(jsonPath("$.hasErrorFile").value(true)).andReturn().getResponse().getContentAsString();
		assertThat(count("select count(*) from catalog.administrative_units where code like 'IMPT-%'")).isEqualTo(2);
		assertThat(count("select count(*) from catalog.administrative_units c join catalog.administrative_units p "
				+ "on p.id = c.parent_id where c.code = 'IMPT-1-A' and p.code = 'IMPT-1'")).isEqualTo(1);
		mockMvc.perform(withAdmin(get("/api/v1/admin/imports/" + jsonId(importId) + "/errors"))).andExpect(status().isOk())
				.andExpect(content().contentType(XLSX));

		upload("administrative-units", file, "false", "UPDATE", null).andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.updated").value(2));
		upload("administrative-units", file, "false", "SKIP", null).andExpect(jsonPath("$.skipped").value(2));
		assertThat(count("select count(*) from catalog.administrative_units where code like 'IMPT-%'")).isEqualTo(2);
	}

	@Test
	@DisplayName("Địa điểm: mã xã không tồn tại → lỗi ở ma_xa; mã địa điểm khớp lại khi import lần hai")
	void venuesMatchByCode() throws Exception {
		byte[] file = workbook(List.of("ma", "ten", "dia_chi", "ma_xa", "suc_chua"),
				List.of("IMPT-V1", "Nhà hát thử", "1 Đường thử", "TEST-79-001", "1500"),
				List.of("IMPT-V2", "Sân thử", "2 Đường thử", "KHONG-CO", "800"),
				List.of("IMPT-V3", "Hội trường thử", "3 Đường thử", "TEST-79-002", "0"));

		upload("venues", file, "false", "UPDATE", null).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1))
				.andExpect(jsonPath("$.errorRows").value(2))
				.andExpect(jsonPath("$.errors[?(@.row == 3 && @.column == 'ma_xa')]").exists())
				.andExpect(jsonPath("$.errors[?(@.row == 4 && @.column == 'suc_chua')]").exists());
		assertThat(count("select count(*) from catalog.venues where code = 'IMPT-V1'")).isEqualTo(1);

		upload("venues", file, "false", "UPDATE", null).andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.updated").value(1));
		assertThat(count("select count(*) from catalog.venues where code like 'IMPT-%'")).isEqualTo(1);
	}

	@Test
	@DisplayName("Sơ đồ ghế: tạo khu IMPORTED với ghế đúng số lượng; trùng ghế báo lỗi; sơ đồ khoá → 409")
	void seatMapImport() throws Exception {
		UUID mapId = UUID.randomUUID();
		jdbc.update("insert into catalog.seat_maps (id, venue_id, name, version, width, height, locked) values (?, ?::uuid, ?, 1, 1000, 800, false)",
				mapId, VENUE, MAP_NAME);
		byte[] file = workbook(List.of("ma_khu", "ten_khu", "hang", "ghe_tu", "ghe_den", "x", "y"),
				List.of("A", "Khu A", "A", "1", "10", "", ""),
				List.of("A", "Khu A", "B", "1", "5", "100", "200"),
				List.of("A", "Khu A", "B", "5", "8", "", ""),
				List.of("A", "Khu A", "C", "5", "2", "", ""));

		upload("seat-maps", file, "true", "UPDATE", mapId).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[?(@.row == 4 && @.column == 'ghe_tu')]").exists())
				.andExpect(jsonPath("$.errors[?(@.row == 5 && @.column == 'ghe_den')]").exists());
		assertThat(count("select count(*) from catalog.sections where seat_map_id = ?", mapId)).isZero();

		upload("seat-maps", file, "false", "UPDATE", mapId).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2))
				.andExpect(jsonPath("$.errorRows").value(2));
		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id "
				+ "where c.seat_map_id = ?", mapId)).isEqualTo(15);
		assertThat(jdbc.queryForObject("select x || ',' || y from catalog.seats s join catalog.sections c on c.id = s.section_id "
				+ "where c.seat_map_id = ? and s.row_label = 'B' and s.seat_number = 2", String.class, mapId)).isEqualTo("128,200");

		upload("seat-maps", file, "false", "UPDATE", mapId).andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.updated").value(2));
		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id "
				+ "where c.seat_map_id = ?", mapId)).isEqualTo(15);

		jdbc.update("update catalog.seat_maps set locked = true where id = ?", mapId);
		upload("seat-maps", file, "false", "UPDATE", mapId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEAT_MAP_LOCKED"));
		upload("seat-maps", file, "false", "UPDATE", null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SEAT_MAP_REQUIRED"));
	}

	@Test
	@DisplayName("File sai định dạng, thiếu cột → 422 IMPORT_FILE_INVALID; không phải Admin → 403")
	void invalidFileAndForbidden() throws Exception {
		upload("venues", "khong phai excel".getBytes(), "true", "UPDATE", null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMPORT_FILE_INVALID"));
		upload("venues", workbook(List.of("ma", "ten"), List.of("X", "Y")), "true", "UPDATE", null)
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IMPORT_FILE_INVALID"));
		mockMvc.perform(get("/api/v1/admin/imports/templates/venues").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("ORGANIZER").bearer())).andExpect(status().isForbidden());
	}

	/** Tìm giá trị {@code id} trong JSON response (không kéo thêm thư viện JSON vào test này). */
	private static String jsonId(String json) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private ResultActions upload(String type, byte[] file, String dryRun, String onDuplicate, UUID seatMapId)
			throws Exception {
		var request = multipart("/api/v1/admin/imports/" + type)
				.file(new MockMultipartFile("file", "import.xlsx", XLSX, file)).param("dryRun", dryRun)
				.param("onDuplicate", onDuplicate);
		if (seatMapId != null) {
			request.param("seatMapId", seatMapId.toString());
		}
		return mockMvc.perform(withAdmin(request));
	}

	private static org.springframework.test.web.servlet.RequestBuilder withAdmin(
			org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> request) {
		return request.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(TestTokens.ADMIN_ID).roles("ADMIN").bearer());
	}

	@SafeVarargs
	private static byte[] workbook(List<String> header, List<String>... rows) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Du lieu");
			Row headerRow = sheet.createRow(0);
			for (int c = 0; c < header.size(); c++) {
				headerRow.createCell(c).setCellValue(header.get(c));
			}
			for (int r = 0; r < rows.length; r++) {
				Row row = sheet.createRow(r + 1);
				for (int c = 0; c < rows[r].size(); c++) {
					if (!rows[r].get(c).isEmpty()) {
						row.createCell(c).setCellValue(rows[r].get(c));
					}
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

}
