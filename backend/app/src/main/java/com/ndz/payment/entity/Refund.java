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

	@Column(name = "booking_id", updatable = false)
	private UUID bookingId;

	@Column(name = "user_id", updatable = false)
	private UUID userId;

	@Column(name = "contact_email", updatable = false)
	private String contactEmail;

	@Column(name = "event_title", updatable = false)
	private String eventTitle;

	@Column(name = "failure_reason")
	private String failureReason;

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

	/** Lần hoàn theo yêu cầu hoàn vé của một booking: mang theo người nhận và tên sự kiện cho event kết quả. */
	public Refund(UUID id, UUID paymentId, long amountMinor, String reason, UUID bookingId, UUID userId,
			String contactEmail, String eventTitle, Instant now) {
		this(id, paymentId, amountMinor, reason, now);
		this.bookingId = bookingId;
		this.userId = userId;
		this.contactEmail = contactEmail;
		this.eventTitle = eventTitle;
	}

	/** Stripe xác nhận đã hoàn xong. Trả false (không đổi gì) nếu lần hoàn này không còn {@code PENDING}. */
	public boolean markSucceeded(Instant now) {
		if (status != RefundStatus.PENDING) {
			return false;
		}
		this.status = RefundStatus.SUCCEEDED;
		this.updatedAt = now;
		return true;
	}

	/** Stripe báo hoàn thất bại. Trả false (không đổi gì) nếu lần hoàn này không còn {@code PENDING}. */
	public boolean markFailed(String failureReason, Instant now) {
		if (status != RefundStatus.PENDING) {
			return false;
		}
		this.status = RefundStatus.FAILED;
		this.failureReason = failureReason;
		this.updatedAt = now;
		return true;
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

	public UUID getBookingId() {
		return bookingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public String getEventTitle() {
		return eventTitle;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getReason() {
		return reason;
	}

	public RefundStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
