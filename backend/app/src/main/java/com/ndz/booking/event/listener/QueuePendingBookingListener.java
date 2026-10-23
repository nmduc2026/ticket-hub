package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.config.BookingProperties;
import com.ndz.booking.event.BookingCancelled;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.BookingCreated;
import com.ndz.booking.event.BookingExpired;
import com.ndz.queue.api.QueueApi;

/**
 * Báo cho phòng chờ biết booking nào đang chờ thanh toán để điều tiết (overview.md §7.1). Booking gọi Queue chứ
 * không để Queue nghe event của Booking, vì Booking đã phụ thuộc Queue (kiểm lượt vào) và Modulith không cho vòng.
 * Mất một lần gọi thì phần tử vẫn tự hết hạn nên số đếm tự lành.
 */
@Component
class QueuePendingBookingListener {

	private final QueueApi queue;
	private final BookingProperties properties;

	QueuePendingBookingListener(QueueApi queue, BookingProperties properties) {
		this.queue = queue;
		this.properties = properties;
	}

	@ApplicationModuleListener
	void on(BookingCreated event) {
		// BR-03: tính tới hạn giữ ghế + ân hạn thanh toán, hạn dài nhất của booking.
		queue.bookingPending(event.eventId(), event.bookingId(), event.expiresAt().plus(properties.paymentGrace()));
	}

	@ApplicationModuleListener
	void on(BookingConfirmed event) {
		queue.bookingSettled(event.eventId(), event.bookingId());
	}

	@ApplicationModuleListener
	void on(BookingExpired event) {
		queue.bookingSettled(event.eventId(), event.bookingId());
	}

	@ApplicationModuleListener
	void on(BookingCancelled event) {
		queue.bookingSettled(event.eventId(), event.bookingId());
	}

}
