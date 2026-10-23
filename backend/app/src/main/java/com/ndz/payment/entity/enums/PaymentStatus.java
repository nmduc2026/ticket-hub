package com.ndz.payment.entity.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Trạng thái thanh toán (overview.md §7.7). {@code payment_failed} là một sự việc, không phải trạng thái. */
public enum PaymentStatus {

	REQUIRES_PAYMENT, PROCESSING, SUCCEEDED, CANCELED;

	private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
			REQUIRES_PAYMENT, EnumSet.of(PROCESSING, SUCCEEDED, CANCELED),
			// payment_failed khi đang xử lý: thẻ bị từ chối, khách thử thẻ khác trên cùng PaymentIntent
			PROCESSING, EnumSet.of(REQUIRES_PAYMENT, SUCCEEDED, CANCELED),
			SUCCEEDED, EnumSet.noneOf(PaymentStatus.class),
			CANCELED, EnumSet.noneOf(PaymentStatus.class));

	public boolean canTransitionTo(PaymentStatus next) {
		return ALLOWED.get(this).contains(next);
	}

}
