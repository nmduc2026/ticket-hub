package com.ndz.booking.service;

import com.ndz.booking.event.PaymentSucceeded;

/** Xác nhận booking khi thanh toán thành công (overview.md §7.2, §7.3, §7.7). */
public interface BookingConfirmationService {

	/**
	 * Booking {@code PENDING_PAYMENT → CONFIRMED}, ghế {@code HELD → SOLD}, phát {@code BookingConfirmed}. Idempotent:
	 * nhận cùng event hai lần cũng chỉ xác nhận một lần. Người gọi (listener) mở transaction.
	 */
	void onPaymentSucceeded(PaymentSucceeded event);

}
