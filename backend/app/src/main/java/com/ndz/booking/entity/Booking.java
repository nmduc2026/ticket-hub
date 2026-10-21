package com.ndz.booking.entity;

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

import com.ndz.booking.entity.enums.BookingStatus;

/**
 * Đơn đặt vé (overview.md §6.2). Entity chỉ được tạo mới ở trạng thái {@code PENDING_PAYMENT}; mọi chuyển trạng thái
 * sau đó đi qua câu lệnh có điều kiện trạng thái cũ ở {@code BookingRepository} (máy trạng thái ở
 * {@link BookingStatus}), nên hai tiến trình đồng thời không cùng chuyển một đơn.
 */
@Entity
@Table(schema = "booking", name = "bookings")
public class Booking {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private UUID eventId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private BookingStatus status;

	@Column(nullable = false, updatable = false)
	private long totalMinor;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3, nullable = false, updatable = false)
	private String currency;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(updatable = false)
	private Instant paymentStartedAt;

	@Column(nullable = false, updatable = false)
	private String contactEmail;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(updatable = false)
	private String refundReason;

	@Column(updatable = false)
	private Long refundAmountMinor;

	@Column(updatable = false)
	private Instant refundRequestedAt;

	@Column(updatable = false)
	private Instant refundFailedAt;

	// Boxed để Spring Data coi đơn chưa lưu là mới (persist, không select trước).
	@Version
	private Long version;

	protected Booking() {
	}

	public Booking(UUID id, UUID userId, UUID eventId, long totalMinor, String currency, Instant expiresAt,
			String contactEmail, Instant now) {
		this.id = id;
		this.userId = userId;
		this.eventId = eventId;
		this.status = BookingStatus.PENDING_PAYMENT;
		this.totalMinor = totalMinor;
		this.currency = currency;
		this.expiresAt = expiresAt;
		this.contactEmail = contactEmail;
		this.createdAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getPaymentStartedAt() {
		return paymentStartedAt;
	}

}
