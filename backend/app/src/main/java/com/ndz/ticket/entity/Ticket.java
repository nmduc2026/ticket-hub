package com.ndz.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ndz.ticket.entity.enums.TicketStatus;

/**
 * Vé của một ghế. Thông tin hiển thị (sự kiện, giờ, địa điểm, ghế) lấy từ {@code BookingConfirmed} và lưu cùng vé nên
 * Ticket không phải đọc dữ liệu của module khác. Index duy nhất {@code uq_ticket_seat_active} ở DB là chốt chặn cuối
 * cho bất biến không bán một ghế cho hai người (BR-01).
 */
@Entity
@Table(schema = "ticket", name = "tickets")
public class Ticket {

	@Id
	private UUID id;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private UUID bookingId;

	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "seat_id", nullable = false, updatable = false)
	private UUID seatId;

	@Column(name = "owner_user_id", nullable = false, updatable = false)
	private UUID ownerUserId;

	@Enumerated(EnumType.STRING)
	private TicketStatus status;

	@Column(name = "qr_token", nullable = false, updatable = false)
	private String qrToken;

	@Column(name = "key_id", nullable = false, updatable = false)
	private String keyId;

	@Column(name = "event_title", nullable = false, updatable = false)
	private String eventTitle;

	@Column(name = "starts_at", nullable = false, updatable = false)
	private Instant startsAt;

	@Column(name = "venue_name", nullable = false, updatable = false)
	private String venueName;

	@Column(name = "section_code", nullable = false, updatable = false)
	private String sectionCode;

	@Column(name = "row_label", nullable = false, updatable = false)
	private String rowLabel;

	@Column(name = "seat_number", nullable = false, updatable = false)
	private int seatNumber;

	@Column(name = "tier_name", nullable = false, updatable = false)
	private String tierName;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Version
	private long version;

	protected Ticket() {
	}

	/** Vé mới còn hiệu lực. Ghế gồm khu, hàng, số; {@code qrToken} là JWT đã ký bằng khoá {@code keyId}. */
	public Ticket(UUID id, UUID bookingId, UUID eventId, UUID seatId, UUID ownerUserId, String qrToken, String keyId,
			String eventTitle, Instant startsAt, String venueName, String sectionCode, String rowLabel,
			int seatNumber, String tierName, Instant issuedAt) {
		this.id = id;
		this.bookingId = bookingId;
		this.eventId = eventId;
		this.seatId = seatId;
		this.ownerUserId = ownerUserId;
		this.status = TicketStatus.VALID;
		this.qrToken = qrToken;
		this.keyId = keyId;
		this.eventTitle = eventTitle;
		this.startsAt = startsAt;
		this.venueName = venueName;
		this.sectionCode = sectionCode;
		this.rowLabel = rowLabel;
		this.seatNumber = seatNumber;
		this.tierName = tierName;
		this.issuedAt = issuedAt;
	}

	/** Thu hồi vé (BR-09). Idempotent: vé đã thu hồi giữ nguyên. Trả true nếu lần gọi này mới đổi trạng thái. */
	public boolean revoke() {
		if (status == TicketStatus.REVOKED) {
			return false;
		}
		this.status = TicketStatus.REVOKED;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getSeatId() {
		return seatId;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public String getQrToken() {
		return qrToken;
	}

	public String getKeyId() {
		return keyId;
	}

	public String getEventTitle() {
		return eventTitle;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public String getVenueName() {
		return venueName;
	}

	public String getSectionCode() {
		return sectionCode;
	}

	public String getRowLabel() {
		return rowLabel;
	}

	public int getSeatNumber() {
		return seatNumber;
	}

	public String getTierName() {
		return tierName;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

}
