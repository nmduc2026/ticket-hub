package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.service.BookingRefundService;

/** Nghe kết quả hoàn tiền do Payment phát; xử lý nằm ở {@link BookingRefundService}. */
@Component
class RefundOutcomeListener {

	private final BookingRefundService refunds;

	RefundOutcomeListener(BookingRefundService refunds) {
		this.refunds = refunds;
	}

	@ApplicationModuleListener
	void on(RefundCompleted event) {
		refunds.onRefundCompleted(event);
	}

	@ApplicationModuleListener
	void on(RefundFailed event) {
		refunds.onRefundFailed(event);
	}

}
