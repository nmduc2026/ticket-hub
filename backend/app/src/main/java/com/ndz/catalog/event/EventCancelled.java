package com.ndz.catalog.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Admin huỷ sự kiện (BR-08): Booking huỷ mọi đơn chờ thanh toán và yêu cầu hoàn 100% cho đơn đã xác nhận (Payment
 * hoàn qua hàng đợi có giới hạn tốc độ); vé bị thu hồi (BR-09); khách nhận thông báo không tắt được.
 */
public record EventCancelled(UUID messageId, Instant occurredAt, UUID eventId, UUID organizerId, String eventTitle,
		String reason) implements IntegrationEvent {
}
