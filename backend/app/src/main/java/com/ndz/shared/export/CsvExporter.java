package com.ndz.shared.export;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Xuất CSV cho danh sách quản trị (overview.md §11.1 "Xuất file"): tối đa {@code maxRows} dòng, mỗi lần xuất ghi
 * audit. Có BOM UTF-8 để Excel đọc đúng tiếng Việt.
 */
@Component
@EnableConfigurationProperties(CsvExporter.ExportProperties.class)
public class CsvExporter {

	public static final String MEDIA_TYPE = "text/csv;charset=UTF-8";

	private static final String BOM = "﻿";

	private final ExportProperties properties;
	private final AuditLogService audit;

	CsvExporter(ExportProperties properties, AuditLogService audit) {
		this.properties = properties;
		this.audit = audit;
	}

	/** Gọi trước khi đọc dữ liệu: quá giới hạn thì {@code EXPORT_TOO_LARGE}, người dùng lọc hẹp lại. */
	public void requireWithinLimit(long rowCount) {
		if (rowCount > properties.maxRows()) {
			throw new BusinessException(ErrorCode.EXPORT_TOO_LARGE,
					"Quá " + properties.maxRows() + " dòng, hãy lọc hẹp lại trước khi xuất")
					.with("rowCount", rowCount).with("maxRows", properties.maxRows());
		}
	}

	public int maxRows() {
		return properties.maxRows();
	}

	/**
	 * Dựng CSV và ghi audit cùng transaction của lời gọi.
	 *
	 * @param what loại danh sách, vd. {@code organizer_bookings}
	 * @param filters bộ lọc đang dùng (ghi vào audit)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public String export(UUID actorId, String what, Map<String, Object> filters, List<String> header,
			List<List<?>> rows) {
		requireWithinLimit(rows.size());
		audit.record(actorId, AuditAction.DATA_EXPORTED, what, actorId, null,
				Map.of("filters", filters, "rows", rows.size()));
		StringBuilder csv = new StringBuilder(BOM).append(line(header));
		rows.forEach(row -> csv.append(line(row)));
		return csv.toString();
	}

	private static String line(List<?> cells) {
		return cells.stream().map(CsvExporter::cell).collect(Collectors.joining(",")) + "\r\n";
	}

	/** RFC 4180: ô có dấu phẩy, nháy kép hoặc xuống dòng thì bọc nháy kép; nháy kép bên trong nhân đôi. */
	private static String cell(Object value) {
		String text = value == null ? "" : value.toString();
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	/** @param maxRows số dòng tối đa mỗi lần xuất (overview.md §11.1: 10.000) */
	@ConfigurationProperties("tickethub.export")
	public record ExportProperties(@DefaultValue("10000") int maxRows) {
	}

}
