package com.ndz.payment.service;

import java.time.Instant;

import com.ndz.payment.entity.Payment;

/**
 * Chốt kết quả của một payment theo máy trạng thái (overview.md §7.7). Dùng chung cho webhook, huỷ PaymentIntent thất
 * bại vì đã {@code succeeded} và đối soát, để quy tắc nằm ở đúng một chỗ. Người gọi mở transaction và đã khoá dòng
 * payment.
 */
public interface PaymentSettlementService {

	/**
	 * Payment thành công: {@code REQUIRES_PAYMENT / PROCESSING → SUCCEEDED} rồi phát {@code PaymentSucceeded}.
	 *
	 * @return false (không đổi gì, không phát event) nếu payment không chuyển được sang SUCCEEDED
	 */
	boolean markSucceeded(Payment payment, Instant now);

	/** Stripe báo đang xử lý: {@code REQUIRES_PAYMENT → PROCESSING}. */
	boolean markProcessing(Payment payment, Instant now);

	/** Payment bị huỷ cùng PaymentIntent: {@code REQUIRES_PAYMENT / PROCESSING → CANCELED}. */
	boolean markCanceled(Payment payment, Instant now);

}
