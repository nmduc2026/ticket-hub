package com.ndz.payment.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ndz.payment.entity.enums.RefundStatus;

/**
 * Một lần thử hoàn tiền (ADR 0026): {@code id} sinh trước khi gọi Stripe và là gốc của idempotency key
 * {@code refund-{id}}, nên gọi lại cùng bản ghi không hoàn hai lần; mỗi lần thử mới là một bản ghi mới.
 */
@Entity
@Table(schema = "payment", name = "refunds")
public class Refund {

	/** Hoàn 100% vì thanh toán đến muộn mà không giữ lại được ghế (overview.md §7.3). */
	public static final String REASON_BOOKING_CONFIRMATION_FAILED = "BOOKING_CONFIRMATION_FAILED";

	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false, updatable = false)
	private UUID paymentId;

	@Column(name = "stripe_refund_id")
	private String stripeRefundId;

	@Column(name = "amount_minor", nullable = false, updatable = false)
	private long amountMinor;

	@Column(nullable = false, updatable = false)
	private String reason;

	@Enumerated(EnumType.STRING)
	private RefundStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Refund() {
	}

	public Refund(UUID id, UUID paymentId, long amountMinor, String reason, Instant now) {
		this.id = id;
		this.paymentId = paymentId;
		this.amountMinor = amountMinor;
		this.reason = reason;
		this.status = RefundStatus.PENDING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Ghi kết quả Stripe trả về: {@code succeeded} là xong, còn lại ({@code pending}...) chờ webhook. */
	public void recordStripeResult(String stripeRefundId, boolean succeeded, Instant now) {
		this.stripeRefundId = stripeRefundId;
		if (succeeded) {
			this.status = RefundStatus.SUCCEEDED;
		}
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public String getStripeRefundId() {
		return stripeRefundId;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public String getReason() {
		return reason;
	}

	public RefundStatus getStatus() {
		return status;
	}

}
