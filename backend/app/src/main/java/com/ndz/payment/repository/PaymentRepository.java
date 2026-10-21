package com.ndz.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepositoryCustom {

	Optional<Payment> findByBookingId(UUID bookingId);

}
