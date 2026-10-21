package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.service.BookingConfirmationService;

/** Nghe {@code PaymentSucceeded} do Payment phát; xử lý nằm ở {@link BookingConfirmationService}. */
@Component
class PaymentSucceededListener {

	private final BookingConfirmationService confirmation;

	PaymentSucceededListener(BookingConfirmationService confirmation) {
		this.confirmation = confirmation;
	}

	@ApplicationModuleListener
	void on(PaymentSucceeded event) {
		confirmation.onPaymentSucceeded(event);
	}

}
