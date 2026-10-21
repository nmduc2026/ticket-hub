package com.ndz.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.payment.entity.Refund;
import com.ndz.payment.entity.enums.RefundStatus;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	/** Lần thử hoàn gần nhất của payment vì một lý do; dùng để gửi lại event không tạo thêm lần hoàn. */
	Optional<Refund> findFirstByPaymentIdAndReasonOrderByCreatedAtDesc(UUID paymentId, String reason);

	Optional<Refund> findByStripeRefundId(String stripeRefundId);

	/** Lần hoàn gần nhất của payment còn chờ Stripe; webhook không mang id hoàn thì dùng lần này. */
	Optional<Refund> findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(UUID paymentId, RefundStatus status);

}
