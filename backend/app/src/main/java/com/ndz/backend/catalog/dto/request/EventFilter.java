package com.ndz.backend.catalog.dto.request;

import java.util.Set;
import java.util.UUID;

import com.ndz.backend.catalog.entity.enums.EventStatus;

/** Điều kiện lọc danh sách sự kiện của organizer; thiếu điều kiện nào thì không lọc theo điều kiện đó. */
public record EventFilter(Set<EventStatus> statuses, UUID venueId, String query) {
}
