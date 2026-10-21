package com.ndz.booking.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tồn kho ghế theo sự kiện (overview.md §6.2). Tạo bằng JPA (trạng thái lấy mặc định {@code AVAILABLE} của DB); đổi
 * trạng thái chỉ bằng câu lệnh có điều kiện {@code WHERE status = ...} ở {@code BookingRepository} để giữ bất biến
 * BR-01, mỗi lần đổi tăng cột {@code version}.
 */
@Entity
@Table(schema = "booking", name = "seat_inventory")
public class SeatInventory {

	@EmbeddedId
	private SeatInventoryId id;

	@Column(nullable = false, updatable = false)
	private UUID tierId;

	@Column(nullable = false, updatable = false)
	private String tierName;

	@Column(nullable = false, updatable = false)
	private String sectionCode;

	@Column(nullable = false, updatable = false)
	private String rowLabel;

	@Column(nullable = false, updatable = false)
	private int seatNumber;

	@Column(nullable = false, updatable = false)
	private long priceMinor;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3, nullable = false, updatable = false)
	private String currency;

	@Column(nullable = false, insertable = false, updatable = false)
	private String status;

	@Column(insertable = false, updatable = false)
	private UUID bookingId;

	@Version
	private Long version;

	protected SeatInventory() {
	}

	public SeatInventory(UUID eventId, UUID seatId, UUID tierId, String tierName, String sectionCode, String rowLabel,
			int seatNumber, long priceMinor, String currency) {
		this.id = new SeatInventoryId(eventId, seatId);
		this.tierId = tierId;
		this.tierName = tierName;
		this.sectionCode = sectionCode;
		this.rowLabel = rowLabel;
		this.seatNumber = seatNumber;
		this.priceMinor = priceMinor;
		this.currency = currency;
	}

	public UUID getSeatId() {
		return id.seatId();
	}

	public UUID getTierId() {
		return tierId;
	}

	public String getTierName() {
		return tierName;
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

	public long getPriceMinor() {
		return priceMinor;
	}

	public String getCurrency() {
		return currency;
	}

	public String getStatus() {
		return status;
	}

	@Embeddable
	public record SeatInventoryId(UUID eventId, UUID seatId) implements Serializable {
	}

}
