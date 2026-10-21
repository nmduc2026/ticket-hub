package com.ndz.shared.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/** Xuất CSV (overview.md §11.1): giới hạn số dòng, thoát ký tự theo RFC 4180, audit mỗi lần xuất. */
class CsvExporterTests {

	private final AuditLogService audit = mock(AuditLogService.class);
	private final CsvExporter exporter = new CsvExporter(new CsvExporter.ExportProperties(2), audit);

	@Test
	@DisplayName("AC-11: quá giới hạn dòng → 422 EXPORT_TOO_LARGE kèm số dòng; đúng giới hạn thì qua")
	void rowLimit() {
		exporter.requireWithinLimit(2);
		assertThatThrownBy(() -> exporter.requireWithinLimit(3)).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.code()).isEqualTo(ErrorCode.EXPORT_TOO_LARGE);
			assertThat(e.properties()).containsEntry("rowCount", 3L).containsEntry("maxRows", 2);
		});
	}

	@Test
	@DisplayName("Ô có dấu phẩy / nháy kép / xuống dòng được bọc nháy kép; có BOM; mỗi lần xuất ghi audit")
	void escapingAndAudit() {
		UUID actor = UUID.randomUUID();
		String csv = exporter.export(actor, "organizer_bookings", Map.of(), List.of("A", "B"),
				List.of(List.of("Rock, Sài Gòn", "nói \"chào\""), List.of("dòng\nhai", 5)));

		assertThat(csv).isEqualTo("﻿A,B\r\n\"Rock, Sài Gòn\",\"nói \"\"chào\"\"\"\r\n\"dòng\nhai\",5\r\n");
		verify(audit).record(any(), any(AuditAction.class), any(), any(), any(), any());
	}

}
