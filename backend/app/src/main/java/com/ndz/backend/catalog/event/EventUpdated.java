package com.ndz.backend.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.backend.shared.event.IntegrationEvent;

/** Thông tin hiển thị của sự kiện đã publish được sửa (BR-14: tiêu đề, mô tả, danh mục, ảnh banner). */
public record EventUpdated(UUID messageId, Instant occurredAt, UUID eventId, String title, String description,
		String category, String bannerUrl) implements IntegrationEvent {
}
