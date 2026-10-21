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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ndz.payment.entity.enums.PaymentStatus;

/**
 * Một thanh toán của một booking (một PaymentIntent). Tiền là số nguyên đơn vị nhỏ nhất (BR-10);
 * {@code applicationFeeMinor} chỉ lưu, chưa gửi lên Stripe khi chưa có Connect (ADR 0022).
 */
@Entity
@Table(schema = "payment", name = "payments")
public class Payment {

	@Id
	private UUID id;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private UUID bookingId;

	@Column(name = "stripe_payment_intent_id", nullable = false, updatable = false)
	private String stripePaymentIntentId;

	@Column(name = "amount_minor", nullable = false, updatable = false)
	private long amountMinor;

	@Column(name = "application_fee_minor", nullable = false, updatable = false)
	private long applicationFeeMinor;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3, nullable = false, updatable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Payment() {
	}

	public Payment(UUID id, UUID bookingId, String stripePaymentIntentId, long amountMinor, long applicationFeeMinor,
			String currency, Instant now) {
		this.id = id;
		this.bookingId = bookingId;
		this.stripePaymentIntentId = stripePaymentIntentId;
		this.amountMinor = amountMinor;
		this.applicationFeeMinor = applicationFeeMinor;
		this.currency = currency;
		this.status = PaymentStatus.REQUIRES_PAYMENT;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public String getStripePaymentIntentId() {
		return stripePaymentIntentId;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public long getApplicationFeeMinor() {
		return applicationFeeMinor;
	}

	public String getCurrency() {
		return currency;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
