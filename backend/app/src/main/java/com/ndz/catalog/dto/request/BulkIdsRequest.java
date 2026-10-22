package com.ndz.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Danh sách id cho thao tác hàng loạt không cần lý do (vd. xoá địa điểm), tối đa 100 (overview.md §11.1). */
public record BulkIdsRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
}
