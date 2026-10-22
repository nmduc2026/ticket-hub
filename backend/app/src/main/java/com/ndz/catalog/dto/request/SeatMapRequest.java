package com.ndz.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;

/** Lưu sơ đồ ghế của Admin (màn #17, ADR 0030): thay toàn bộ khu và phần tử không bán; ghế sinh lại từ tham số khu. */
@Schema(name = "SeatMapRequest")
public record SeatMapRequest(
		@Schema(description = "Bắt buộc khi tạo; bỏ qua khi sửa") UUID venueId,
		@NotBlank @Size(max = 120) String name,
		@Min(100) @Max(20000) int width,
		@Min(100) @Max(20000) int height,
		@NotNull @Size(max = 200) List<@Valid SectionInput> sections,
		@NotNull @Size(max = 500) List<@Valid ElementInput> elements) {

	@Schema(name = "SeatMapSectionInput")
	public record SectionInput(
			@NotBlank @Size(max = 30) String code,
			@NotBlank @Size(max = 120) String name,
			@Schema(description = "GRID: kind, x, y, rotation, rows, seatsPerRow, rowGap, seatGap, curve, numbering, hiddenSeats. "
					+ "IMPORTED: chỉ đổi được vị trí / góc xoay, ghế giữ nguyên") @NotNull JsonNode shape) {
	}

	@Schema(name = "SeatMapElementInput")
	public record ElementInput(
			@Pattern(regexp = "STAGE|AISLE|LABEL|AREA") String type,
			@Size(max = 120) String label,
			@Schema(description = "x, y, w, h, rotation") @NotNull JsonNode shape) {
	}

}
