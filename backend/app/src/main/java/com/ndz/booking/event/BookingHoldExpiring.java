package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Booking chưa bắt đầu thanh toán còn ít thời gian giữ ghế (overview.md §6.5, BR-03): Notification báo khách quay lại
 * thanh toán. Phát một lần cho mỗi booking; mang đủ dữ liệu để gửi mà không hỏi ngược Booking.
 */
public record BookingHoldExpiring(UUID messageId, Instant occurredAt, UUID bookingId, UUID userId, UUID eventId,
		String eventTitle, String contactEmail, Instant expiresAt) implements IntegrationEvent {
}
