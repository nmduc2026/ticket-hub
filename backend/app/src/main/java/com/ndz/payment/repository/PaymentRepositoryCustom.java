package com.ndz.payment.repository;

import com.ndz.payment.entity.Payment;

/** Phần của {@link PaymentRepository} viết bằng SQL. */
public interface PaymentRepositoryCustom {

	/**
	 * Thêm payment nếu booking chưa có (unique {@code booking_id}); hai request cùng lúc chỉ một bản ghi được thêm.
	 *
	 * @return true nếu đã thêm; false nếu booking đã có payment
	 */
	boolean insertIfAbsent(Payment payment);

}
