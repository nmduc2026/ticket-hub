package com.ndz.payment.service;

import java.util.UUID;

/** Hoàn tiền tự động (overview.md §7.3, §7.4, ADR 0026). Hoàn theo yêu cầu khách (BR-07) làm ở Sprint 4. */
public interface PaymentRefundService {

	/**
	 * Thanh toán đến muộn mà booking không giữ lại được ghế: hoàn 100% cho payment. Mỗi lần thử là một bản ghi
	 * {@code refunds} có id sinh trước khi gọi Stripe (idempotency key {@code refund-{refundId}}); nhận lại event thì
	 * dùng lại bản ghi cũ nên không hoàn hai lần. Idempotent theo {@code messageId}. Người gọi (listener) mở transaction.
	 */
	void refundFully(UUID messageId, UUID paymentId, String reason);

}
