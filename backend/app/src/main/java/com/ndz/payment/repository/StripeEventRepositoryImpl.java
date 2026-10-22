package com.ndz.payment.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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

	@Override
	public List<EventSummary> findByPaymentIntent(String paymentIntentId) {
		return jdbc.query("""
				SELECT stripe_event_id, type, received_at, processed_at FROM payment.stripe_events
				WHERE payload #>> '{data,object,id}' = ? OR payload #>> '{data,object,payment_intent}' = ?
				ORDER BY received_at
				""", (rs, i) -> new EventSummary(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
				rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()), paymentIntentId, paymentIntentId);
	}

}
