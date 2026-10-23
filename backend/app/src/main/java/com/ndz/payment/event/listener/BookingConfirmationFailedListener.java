package com.ndz.payment.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingConfirmationFailed;
import com.ndz.payment.entity.Refund;
import com.ndz.payment.service.PaymentRefundService;

/** Nghe {@code BookingConfirmationFailed} do Booking phát để hoàn tiền tự động; xử lý nằm ở service. */
@Component
class BookingConfirmationFailedListener {

	private final PaymentRefundService refunds;

	BookingConfirmationFailedListener(PaymentRefundService refunds) {
		this.refunds = refunds;
	}

	@ApplicationModuleListener
	void on(BookingConfirmationFailed event) {
		refunds.refundFully(event.messageId(), event.paymentId(), Refund.REASON_BOOKING_CONFIRMATION_FAILED);
	}

}
