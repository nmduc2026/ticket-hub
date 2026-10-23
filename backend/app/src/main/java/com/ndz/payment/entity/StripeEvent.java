package com.ndz.payment.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Webhook của Stripe đã nhận, để không xử lý trùng (overview.md §6.3). Ghi cùng transaction với việc xử lý nên webhook
 * xử lý lỗi thì bản ghi này cũng hoàn tác và Stripe gửi lại.
 */
@Entity
@Table(schema = "payment", name = "stripe_events")
public class StripeEvent {

	@Id
	@Column(name = "stripe_event_id")
	private String id;

	private String type;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String payload;

	@Column(name = "received_at")
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	protected StripeEvent() {
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

}
