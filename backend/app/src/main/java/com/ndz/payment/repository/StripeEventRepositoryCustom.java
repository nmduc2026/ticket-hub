package com.ndz.payment.repository;

import java.time.Instant;

/** Phần của {@link StripeEventRepository} viết bằng SQL. */
public interface StripeEventRepositoryCustom {

	/**
	 * Ghi webhook nếu chưa nhận bao giờ; hai lần gửi cùng lúc chỉ một lần được ghi.
	 *
	 * @return true nếu đây là lần đầu (cứ xử lý tiếp); false nếu đã nhận trước đó (bỏ qua)
	 */
	boolean insertIfAbsent(String stripeEventId, String type, String payload, Instant receivedAt);

	void markProcessed(String stripeEventId, Instant processedAt);

}
