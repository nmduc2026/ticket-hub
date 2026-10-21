package com.ndz.payment.repository;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;

import com.ndz.payment.entity.Payment;

public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

	private final JdbcTemplate jdbc;

	PaymentRepositoryImpl(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean insertIfAbsent(Payment payment) {
		return jdbc.update("""
				INSERT INTO payment.payments (id, booking_id, stripe_payment_intent_id, amount_minor,
				                              application_fee_minor, currency, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (booking_id) DO NOTHING
				""", payment.getId(), payment.getBookingId(), payment.getStripePaymentIntentId(),
				payment.getAmountMinor(), payment.getApplicationFeeMinor(), payment.getCurrency(),
				payment.getStatus().name(), Timestamp.from(payment.getCreatedAt()),
				Timestamp.from(payment.getUpdatedAt())) == 1;
	}

}
