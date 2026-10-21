package com.ndz.payment.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.ndz.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepositoryCustom {

	Optional<Payment> findByBookingId(UUID bookingId);

	/** Khoá dòng payment để webhook, đối soát và huỷ không chuyển trạng thái chồng lên nhau. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

}
