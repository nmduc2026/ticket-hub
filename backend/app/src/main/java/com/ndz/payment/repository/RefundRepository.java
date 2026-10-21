package com.ndz.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.payment.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	/** Lần thử hoàn gần nhất của payment vì một lý do; dùng để gửi lại event không tạo thêm lần hoàn. */
	Optional<Refund> findFirstByPaymentIdAndReasonOrderByCreatedAtDesc(UUID paymentId, String reason);

}
