package com.ndz.booking.dto.request;

import java.util.List;
import java.util.UUID;

/** Bộ lọc danh sách đơn toàn hệ thống của Admin (overview.md §11.2). */
public record AdminBookingFilter(List<String> statuses, UUID organizerId, Boolean needsReconcile, String q) {
}
