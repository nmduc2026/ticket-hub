package com.ndz.catalog.controller;

import java.io.IOException;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ndz.catalog.dto.response.ImportResponses.ImportResult;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.service.AdminImportService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.CurrentUser;

/** Import Excel của Admin cho màn #16, #17, #33 (overview.md §11.2, docs/design/README.md §7). */
@RestController
@RequestMapping("/api/v1/admin/imports")
@PreAuthorize("hasRole('ADMIN')")
class AdminImportController {

	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final AdminImportService service;

	AdminImportController(AdminImportService service) {
		this.service = service;
	}

	@Hidden
	@GetMapping("/templates/{type}")
	ResponseEntity<byte[]> template(@PathVariable String type) {
		ImportType importType = parse(type);
		return download(service.template(importType), importType.templateFileName());
	}

	@Operation(operationId = "importExcel",
			summary = "Kiểm file Excel (dryRun=true) hoặc ghi các dòng hợp lệ (dryRun=false); type: administrative-units, venues, seat-maps")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ImportResult.class)))
	@ApiResponse(responseCode = "422",
			description = "IMPORT_FILE_INVALID, IMPORT_FILE_TOO_LARGE, IMPORT_TOO_MANY_ROWS, EMPTY_FILE hoặc SEAT_MAP_REQUIRED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "SEAT_MAP_LOCKED hoặc SEAT_MAP_BEING_EDITED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(path = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ImportResult importFile(@PathVariable String type, @RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun,
			@RequestParam(defaultValue = "UPDATE") DuplicateMode onDuplicate,
			@RequestParam(required = false) UUID seatMapId) throws IOException {
		return service.importFile(CurrentUser.require().id(), parse(type), file.getBytes(), dryRun, onDuplicate,
				seatMapId);
	}

	@Hidden
	@GetMapping("/{id}/errors")
	ResponseEntity<byte[]> errors(@PathVariable UUID id) {
		return download(service.errorFile(id), "loi-import.xlsx");
	}

	private static ImportType parse(String slug) {
		return ImportType.fromSlug(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_TYPE_INVALID, "Loại import không hợp lệ"));
	}

	private static ResponseEntity<byte[]> download(byte[] content, String fileName) {
		return ResponseEntity.ok().contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
				.body(content);
	}

}
