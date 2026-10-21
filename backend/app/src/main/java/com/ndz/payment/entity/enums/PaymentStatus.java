package com.ndz.payment.entity.enums;

/** Trạng thái thanh toán (overview.md §7.7). {@code payment_failed} là một sự việc, không phải trạng thái. */
public enum PaymentStatus {

	REQUIRES_PAYMENT, PROCESSING, SUCCEEDED, CANCELED

}
