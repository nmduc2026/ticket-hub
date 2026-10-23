package com.ndz.booking.event;

import java.time.Instant;
import java.util.UUID;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Thanh toán thành công đến muộn (booking đã hết hạn hoặc bị huỷ) nhưng không giữ lại được đúng các ghế đó: Payment tự
 * động hoàn 100% (overview.md §7.3). Booking giữ nguyên trạng thái. {@code reason} là {@code EVENT_NOT_ON_SALE} hoặc
 * {@code SEATS_UNAVAILABLE}. Khai báo ở module Booking vì Payment là bên nhận (xem {@link PaymentSucceeded}).
 */
public record BookingConfirmationFailed(UUID messageId, Instant occurredAt, UUID bookingId, UUID paymentId,
		String reason) implements IntegrationEvent {

	public static final String EVENT_NOT_ON_SALE = "EVENT_NOT_ON_SALE";
	public static final String SEATS_UNAVAILABLE = "SEATS_UNAVAILABLE";

}
