package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Payment phát hiện (hoặc hết) lệch đối soát của một đơn (overview.md §16.3): payment đã quá hạn đối soát mà booking
 * chưa {@code CONFIRMED} và chưa được hoàn. Booking chỉ lưu cờ {@code needs_reconcile} để Admin lọc được (màn #29),
 * không đổi trạng thái đơn.
 */
public record PaymentReconcileFlagged(UUID messageId, Instant occurredAt, UUID bookingId, boolean flagged)
		implements IntegrationEvent {
}
