package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Thông tin hiển thị của sự kiện đã publish được sửa (BR-14: tiêu đề, mô tả, danh mục, ảnh banner, giờ mở cửa, độ
 * tuổi, quy định). Mang toàn bộ thông tin hiển thị hiện tại, không chỉ trường vừa đổi.
 */
public record EventUpdated(UUID messageId, Instant occurredAt, UUID eventId, String title, String description,
		String category, String bannerUrl, Instant doorsOpenAt, Short ageRestriction, String rules)
		implements IntegrationEvent {
}
