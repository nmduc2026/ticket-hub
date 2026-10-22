package com.ndz.catalog.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ndz.catalog.entity.enums.AdministrativeUnitType;

/**
 * Đơn vị hành chính, hai cấp trong một bảng: cấp 1 Tỉnh / Thành phố, cấp 2 Xã / Phường / Đặc khu (overview.md §6.1).
 * Xoá chỉ là {@code isActive = false}; không xoá cứng. Không tắt được khi còn con (cấp 1) hoặc còn địa điểm (cấp 2);
 * service kiểm trước khi đổi.
 */
@Entity
@Table(schema = "catalog", name = "administrative_units")
public class AdministrativeUnit {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdministrativeUnitType type;

	@Column(nullable = false, updatable = false)
	private short level;

	@Column(name = "parent_id", updatable = false)
	private UUID parentId;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Version
	private Long version;

	protected AdministrativeUnit() {
	}

	public AdministrativeUnit(UUID id, String code, String name, AdministrativeUnitType type, short level,
			UUID parentId, LocalDate effectiveFrom) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.type = type;
		this.level = level;
		this.parentId = parentId;
		this.effectiveFrom = effectiveFrom;
	}

	public void update(String name, String code, LocalDate effectiveFrom) {
		this.name = name;
		this.code = code;
		this.effectiveFrom = effectiveFrom;
	}

	public void disable() {
		this.active = false;
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

	public AdministrativeUnitType getType() {
		return type;
	}

	public short getLevel() {
		return level;
	}

	public UUID getParentId() {
		return parentId;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

}
