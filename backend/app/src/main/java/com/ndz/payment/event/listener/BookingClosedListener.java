package com.ndz.payment.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingCancelled;
import com.ndz.booking.event.BookingExpired;
import com.ndz.payment.service.PaymentCancellationService;

/** Nghe booking hết hạn hoặc bị huỷ do Booking phát để huỷ PaymentIntent; xử lý nằm ở service. */
@Component
class BookingClosedListener {

	private final PaymentCancellationService cancellation;

	BookingClosedListener(PaymentCancellationService cancellation) {
		this.cancellation = cancellation;
	}

	@ApplicationModuleListener
	void on(BookingExpired event) {
		cancellation.onBookingClosed(event.messageId(), event.bookingId());
	}

	@ApplicationModuleListener
	void on(BookingCancelled event) {
		cancellation.onBookingClosed(event.messageId(), event.bookingId());
	}

}
