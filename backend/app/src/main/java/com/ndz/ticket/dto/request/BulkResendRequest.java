package com.ndz.ticket.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Danh sách đơn gửi lại email vé, tối đa 100 (overview.md §11.1). */
public record BulkResendRequest(@NotEmpty @Size(max = 100) List<UUID> bookingIds) {
}
