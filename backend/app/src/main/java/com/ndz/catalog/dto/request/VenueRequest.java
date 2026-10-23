package com.ndz.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Tạo / sửa địa điểm (overview.md §11.2). */
public record VenueRequest(@NotBlank String name, @NotBlank String address, @NotNull UUID wardId,
		@Min(1) int capacity) {
}
