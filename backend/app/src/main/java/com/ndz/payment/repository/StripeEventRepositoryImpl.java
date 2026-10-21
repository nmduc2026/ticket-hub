package com.ndz.payment.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

public class StripeEventRepositoryImpl implements StripeEventRepositoryCustom {

	private final JdbcTemplate jdbc;

	StripeEventRepositoryImpl(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean insertIfAbsent(String stripeEventId, String type, String payload, Instant receivedAt) {
		return jdbc.update("""
				INSERT INTO payment.stripe_events (stripe_event_id, type, payload, received_at)
				VALUES (?, ?, ?::jsonb, ?)
				ON CONFLICT (stripe_event_id) DO NOTHING
				""", stripeEventId, type, payload, Timestamp.from(receivedAt)) == 1;
	}

	@Override
	public void markProcessed(String stripeEventId, Instant processedAt) {
		jdbc.update("UPDATE payment.stripe_events SET processed_at = ? WHERE stripe_event_id = ?",
				Timestamp.from(processedAt), stripeEventId);
	}

}
