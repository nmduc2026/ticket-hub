package com.ndz.catalog.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Địa điểm (overview.md §6.1). {@code wardId} phải trỏ đơn vị hành chính cấp 2; service kiểm, FK không kiểm được. */
@Entity
@Table(schema = "catalog", name = "venues")
public class Venue {

	@Id
	private UUID id;

	/** Mã để khớp khi import lại; trống với địa điểm tạo tay. */
	@Column
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(name = "ward_id", nullable = false)
	private UUID wardId;

	@Column(nullable = false)
	private int capacity;

	protected Venue() {
	}

	public Venue(UUID id, String name, String address, UUID wardId, int capacity) {
		this.id = id;
		this.name = name;
		this.address = address;
		this.wardId = wardId;
		this.capacity = capacity;
	}

	/** Địa điểm tạo từ import Excel, khớp lại theo {@code code}. */
	public Venue(UUID id, String code, String name, String address, UUID wardId, int capacity) {
		this(id, name, address, wardId, capacity);
		this.code = code;
	}

	public void update(String name, String address, UUID wardId, int capacity) {
		this.name = name;
		this.address = address;
		this.wardId = wardId;
		this.capacity = capacity;
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public UUID getWardId() {
		return wardId;
	}

	public int getCapacity() {
		return capacity;
	}

}
