package com.ndz.backend.catalog.events;

import java.time.Instant;
import java.util.UUID;

import com.ndz.backend.shared.event.IntegrationEvent;

/**
 * Booking báo đã tạo xong tồn kho ghế của sự kiện (overview.md §7.9); Catalog nghe để đặt {@code inventory_ready}.
 * Kiểu dữ liệu nằm ở Catalog (bên nhận định nghĩa điều nó cần) để hai module không phụ thuộc vòng; trên Kafka đây
 * vẫn là message của topic {@code booking.events.v1}.
 */
public record SeatInventoryReady(UUID messageId, Instant occurredAt, UUID eventId, int seatCount)
		implements IntegrationEvent {
}
