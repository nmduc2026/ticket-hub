package com.ndz.catalog.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ndz.catalog.entity.enums.EventStatus;

/** Bộ lọc danh sách sự kiện của Admin (overview.md §11.2). */
public record AdminEventFilter(EventStatus status, UUID organizerId, Instant from, Instant to, String q) {
}
