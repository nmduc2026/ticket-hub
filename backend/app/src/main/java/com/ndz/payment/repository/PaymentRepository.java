package com.ndz.payment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepositoryCustom {

	Optional<Payment> findByBookingId(UUID bookingId);

	/** Payment chưa kết thúc tạo không muộn hơn {@code cutoff}, cũ nhất trước, để đối soát với Stripe (theo lô). */
	List<Payment> findByStatusInAndCreatedAtLessThanEqualOrderByCreatedAt(Collection<PaymentStatus> statuses, Instant cutoff,
			Pageable page);

	/**
	 * Payment đã thu tiền từ trước {@code cutoff} mà chưa có lần hoàn nào đang chạy hoặc đã xong: ứng viên của chỉ số
	 * "đã trả tiền mà chưa có vé hay hoàn" (Booking cho biết booking đã {@code CONFIRMED} chưa).
	 */
	@Query("""
			select p from Payment p where p.status = com.ndz.payment.entity.enums.PaymentStatus.SUCCEEDED
			  and p.updatedAt < :cutoff
			  and not exists (select r.id from Refund r where r.paymentId = p.id
			                  and r.status in (com.ndz.payment.entity.enums.RefundStatus.PENDING,
			                                   com.ndz.payment.entity.enums.RefundStatus.SUCCEEDED))
			""")
	List<Payment> findSucceededWithoutRefundBefore(@Param("cutoff") Instant cutoff);

	/** Khoá dòng payment để webhook, đối soát và huỷ không chuyển trạng thái chồng lên nhau. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

}
