package com.ndz.catalog.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ndz.catalog.entity.enums.AdministrativeUnitType;

/** Tạo / sửa đơn vị hành chính (overview.md §11.2). {@code parentId} bắt buộc với cấp 2, phải trống với cấp 1. */
public record AdminUnitRequest(@NotBlank String code, @NotBlank String name, @NotNull AdministrativeUnitType type,
		UUID parentId, @NotNull LocalDate effectiveFrom) {
}
