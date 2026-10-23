package com.ndz.catalog.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.ndz.catalog.constant.ImportLimits;
import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/** Đọc và ghi file .xlsx cho import Excel: đọc file người dùng tải lên, sinh file mẫu và file lỗi. */
public final class ExcelFiles {

	public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	private static final String DATA_SHEET = "Du lieu";
	private static final String GUIDE_SHEET = "Huong dan";

	/** Một dòng dữ liệu: số dòng Excel (dòng 1 là tiêu đề) và giá trị theo tên cột, ô trống là chuỗi rỗng. */
	public record ImportRow(int number, Map<String, String> cells) {

		public String get(String column) {
			return cells.getOrDefault(column, "");
		}
	}

	/** Một dòng của sheet hướng dẫn: tên cột, bắt buộc hay không, mô tả giá trị hợp lệ. */
	public record GuideLine(String column, boolean required, String description) {
	}

	private ExcelFiles() {
	}

	/**
	 * Đọc sheet đầu tiên. Dòng 1 là tiêu đề, phải có đủ {@code columns} (thứ tự tuỳ ý, thừa cột thì bỏ qua); dòng trống bỏ qua.
	 */
	public static List<ImportRow> read(byte[] bytes, List<String> columns) {
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			Sheet sheet = workbook.getSheetAt(0);
			Row header = sheet.getRow(sheet.getFirstRowNum());
			if (header == null) {
				throw invalid("File không có dòng tiêu đề");
			}
			DataFormatter formatter = new DataFormatter();
			Map<String, Integer> index = new HashMap<>();
			for (Cell cell : header) {
				index.putIfAbsent(text(cell, formatter).toLowerCase(), cell.getColumnIndex());
			}
			List<String> missing = columns.stream().filter(column -> !index.containsKey(column)).toList();
			if (!missing.isEmpty()) {
				throw invalid("Thiếu cột: " + String.join(", ", missing));
			}
			List<ImportRow> rows = new ArrayList<>();
			for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
				Row row = sheet.getRow(r);
				if (row == null) {
					continue;
				}
				Map<String, String> cells = new HashMap<>();
				boolean blank = true;
				for (String column : columns) {
					String value = text(row.getCell(index.get(column)), formatter);
					cells.put(column, value);
					blank &= value.isEmpty();
				}
				if (blank) {
					continue;
				}
				if (rows.size() >= ImportLimits.MAX_ROWS) {
					throw new BusinessException(ErrorCode.IMPORT_TOO_MANY_ROWS,
							"File có quá " + ImportLimits.MAX_ROWS + " dòng");
				}
				rows.add(new ImportRow(r + 1, cells));
			}
			return rows;
		}
		catch (IOException | RuntimeException e) {
			if (e instanceof BusinessException business) {
				throw business;
			}
			throw invalid("Không đọc được file, cần đúng định dạng .xlsx");
		}
	}

	/** File mẫu: sheet dữ liệu có tiêu đề + dòng ví dụ, sheet hướng dẫn liệt kê cột và giá trị hợp lệ. */
	public static byte[] template(List<String> columns, Set<String> textColumns, List<List<String>> examples,
			List<GuideLine> guide) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle bold = boldStyle(workbook);
			CellStyle textStyle = workbook.createCellStyle();
			textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

			Sheet data = workbook.createSheet(DATA_SHEET);
			Row header = data.createRow(0);
			for (int c = 0; c < columns.size(); c++) {
				Cell cell = header.createCell(c);
				cell.setCellValue(columns.get(c));
				cell.setCellStyle(bold);
				if (textColumns.contains(columns.get(c))) {
					data.setDefaultColumnStyle(c, textStyle);
				}
				data.setColumnWidth(c, 20 * 256);
			}
			for (int r = 0; r < examples.size(); r++) {
				Row row = data.createRow(r + 1);
				for (int c = 0; c < columns.size(); c++) {
					String value = examples.get(r).get(c);
					if (!value.isEmpty()) {
						row.createCell(c).setCellValue(value);
					}
				}
			}

			Sheet help = workbook.createSheet(GUIDE_SHEET);
			Row helpHeader = help.createRow(0);
			for (String title : List.of("Cot", "Bat buoc", "Gia tri hop le")) {
				Cell cell = helpHeader.createCell(helpHeader.getLastCellNum() < 0 ? 0 : helpHeader.getLastCellNum());
				cell.setCellValue(title);
				cell.setCellStyle(bold);
			}
			for (int r = 0; r < guide.size(); r++) {
				Row row = help.createRow(r + 1);
				row.createCell(0).setCellValue(guide.get(r).column());
				row.createCell(1).setCellValue(guide.get(r).required() ? "Co" : "Khong");
				row.createCell(2).setCellValue(guide.get(r).description());
			}
			help.setColumnWidth(0, 18 * 256);
			help.setColumnWidth(1, 12 * 256);
			help.setColumnWidth(2, 80 * 256);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Không tạo được file mẫu", e);
		}
	}

	/** File lỗi: mỗi lỗi một dòng gồm dòng Excel, cột, nội dung. */
	public static byte[] errorFile(List<RowError> errors) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle bold = boldStyle(workbook);
			Sheet sheet = workbook.createSheet("Loi");
			Row header = sheet.createRow(0);
			String[] titles = { "dong", "cot", "loi" };
			for (int c = 0; c < titles.length; c++) {
				Cell cell = header.createCell(c);
				cell.setCellValue(titles[c]);
				cell.setCellStyle(bold);
			}
			for (int r = 0; r < errors.size(); r++) {
				Row row = sheet.createRow(r + 1);
				row.createCell(0).setCellValue(errors.get(r).row());
				row.createCell(1).setCellValue(errors.get(r).column());
				row.createCell(2).setCellValue(errors.get(r).message());
			}
			sheet.setColumnWidth(1, 18 * 256);
			sheet.setColumnWidth(2, 80 * 256);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Không tạo được file lỗi", e);
		}
	}

	private static CellStyle boldStyle(Workbook workbook) {
		Font font = workbook.createFont();
		font.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(font);
		return style;
	}

	/** Ngày trả về dạng yyyy-MM-dd; số nguyên bỏ phần ".0"; còn lại theo định dạng hiển thị của ô. */
	private static String text(Cell cell, DataFormatter formatter) {
		if (cell == null) {
			return "";
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			if (DateUtil.isCellDateFormatted(cell)) {
				return cell.getLocalDateTimeCellValue().toLocalDate().toString();
			}
			double value = cell.getNumericCellValue();
			return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value)
					: BigDecimal.valueOf(value).toPlainString();
		}
		return formatter.formatCellValue(cell).trim();
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.IMPORT_FILE_INVALID, message);
	}

}
