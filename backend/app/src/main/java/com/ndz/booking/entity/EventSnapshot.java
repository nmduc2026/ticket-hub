package com.ndz.booking.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản sao thông tin sự kiện của Booking (overview.md §6.2), cập nhật từ event của Catalog. Chỉ đọc qua JPA; ghi bằng
 * câu lệnh có điều kiện {@code last_event_at < occurredAt} ở {@code EventSnapshotRepository} để event đến sai thứ tự
 * không ghi đè dữ liệu mới.
 */
@Entity
@Table(schema = "booking", name = "event_snapshots")
public class EventSnapshot {

	@Id
	private UUID eventId;

	private UUID organizerId;

	private String title;

	private String description;

	private String category;

	private String bannerUrl;

	private UUID venueId;

	private String venueName;

	private UUID cityId;

	private String cityName;

	private UUID seatMapId;

	private Instant startsAt;

	private Instant endsAt;

	private Instant saleStartsAt;

	private Instant doorsOpenAt;

	private Short ageRestriction;

	private String rules;

	private String status;

	private boolean highDemand;

	private short maxTicketsPerUser;

	private int platformFeeBps;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3)
	private String currency;

	private Instant lastEventAt;

	@Version
	private Long version;

	protected EventSnapshot() {
	}

	public String getStatus() {
		return status;
	}

	public int getMaxTicketsPerUser() {
		return maxTicketsPerUser;
	}

	public Instant getSaleStartsAt() {
		return saleStartsAt;
	}

	public boolean isHighDemand() {
		return highDemand;
	}

}
