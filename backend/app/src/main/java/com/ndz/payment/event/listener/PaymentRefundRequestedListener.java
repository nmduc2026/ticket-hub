package com.ndz.payment.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundRequested;
import com.ndz.payment.service.PaymentRefundService;

/** Nghe {@code RefundRequested} do Booking phát để hoàn tiền; xử lý nằm ở {@link PaymentRefundService}. */
@Component
class PaymentRefundRequestedListener {

	private final PaymentRefundService refunds;

	PaymentRefundRequestedListener(PaymentRefundService refunds) {
		this.refunds = refunds;
	}

	@ApplicationModuleListener
	void on(RefundRequested event) {
		refunds.refundForBooking(event);
	}

}
