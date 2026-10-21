package com.ndz.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.payment.entity.StripeEvent;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String>, StripeEventRepositoryCustom {
}
