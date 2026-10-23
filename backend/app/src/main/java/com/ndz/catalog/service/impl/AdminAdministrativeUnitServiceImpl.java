package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.request.AdminUnitRequest;
import com.ndz.catalog.dto.response.AdminUnitResponse;
import com.ndz.catalog.entity.AdministrativeUnit;
import com.ndz.catalog.entity.enums.AdministrativeUnitType;
import com.ndz.catalog.repository.AdministrativeUnitRepository;
import com.ndz.catalog.repository.VenueRepository;
import com.ndz.catalog.service.AdminAdministrativeUnitService;
import com.ndz.catalog.util.AdministrativeUnitCache;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
class AdminAdministrativeUnitServiceImpl implements AdminAdministrativeUnitService {

	private final AdministrativeUnitRepository units;
	private final VenueRepository venues;
	private final AuditLogService audit;
	private final AdministrativeUnitCache cache;

	AdminAdministrativeUnitServiceImpl(AdministrativeUnitRepository units, VenueRepository venues,
			AuditLogService audit, AdministrativeUnitCache cache) {
		this.units = units;
		this.venues = venues;
		this.audit = audit;
		this.cache = cache;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminUnitResponse> list(Integer level, UUID parentId) {
		List<AdministrativeUnit> rows;
		if (parentId != null) {
			rows = units.findByParentIdOrderByName(parentId);
		}
		else if (level != null) {
			rows = units.findByLevelOrderByName(level.shortValue());
		}
		else {
			rows = units.findAll();
		}
		return rows.stream().map(AdminAdministrativeUnitServiceImpl::toResponse).toList();
	}

	@Override
	@Transactional
	public AdminUnitResponse create(UUID adminId, AdminUnitRequest request) {
		if (units.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.ADMIN_UNIT_CODE_DUPLICATE, "Mã đơn vị đã tồn tại");
		}
		short level = levelOf(request.type());
		AdministrativeUnit parent = requireParent(level, request.parentId());
		AdministrativeUnit unit = new AdministrativeUnit(Uuid7.next(), request.code().trim(), request.name().trim(),
				request.type(), level, parent == null ? null : parent.getId(), request.effectiveFrom());
		units.save(unit);
		audit.record(adminId, AuditAction.ADMIN_UNIT_CREATED, "administrative_unit", unit.getId(), null,
				Map.of("code", unit.getCode(), "name", unit.getName()));
		cache.evictAfterCommit();
		return toResponse(unit);
	}

	@Override
	@Transactional
	public AdminUnitResponse update(UUID adminId, UUID id, AdminUnitRequest request) {
		AdministrativeUnit unit = units.lockById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_UNIT_NOT_FOUND, "Không tìm thấy đơn vị"));
		if (units.existsByCodeAndIdNot(request.code(), id)) {
			throw new BusinessException(ErrorCode.ADMIN_UNIT_CODE_DUPLICATE, "Mã đơn vị đã tồn tại");
		}
		Map<String, Object> before = Map.of("code", unit.getCode(), "name", unit.getName());
		unit.update(request.name().trim(), request.code().trim(), request.effectiveFrom());
		audit.record(adminId, AuditAction.ADMIN_UNIT_UPDATED, "administrative_unit", id, before,
				Map.of("code", unit.getCode(), "name", unit.getName()));
		cache.evictAfterCommit();
		return toResponse(unit);
	}

	@Override
	@Transactional
	public void disable(UUID adminId, UUID id) {
		AdministrativeUnit unit = units.lockById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_UNIT_NOT_FOUND, "Không tìm thấy đơn vị"));
		boolean inUse = unit.getLevel() == 1 ? units.existsByParentIdAndActiveTrue(id) : venues.existsByWardId(id);
		if (inUse) {
			throw new BusinessException(ErrorCode.ADMIN_UNIT_IN_USE,
					unit.getLevel() == 1 ? "Còn đơn vị con đang hoạt động" : "Còn địa điểm thuộc đơn vị này");
		}
		unit.disable();
		cache.evictAfterCommit();
		audit.record(adminId, AuditAction.ADMIN_UNIT_DISABLED, "administrative_unit", id,
				Map.of("active", true), Map.of("active", false));
	}

	private AdministrativeUnit requireParent(short level, UUID parentId) {
		if (level == 1) {
			if (parentId != null) {
				throw new BusinessException(ErrorCode.ADMIN_UNIT_PARENT_INVALID, "Cấp Tỉnh / Thành phố không có đơn vị cha");
			}
			return null;
		}
		if (parentId == null) {
			throw new BusinessException(ErrorCode.ADMIN_UNIT_PARENT_INVALID, "Cấp Xã / Phường phải chọn Tỉnh / Thành phố");
		}
		AdministrativeUnit parent = units.findById(parentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_UNIT_PARENT_INVALID, "Không tìm thấy đơn vị cha"));
		if (parent.getLevel() != 1) {
			throw new BusinessException(ErrorCode.ADMIN_UNIT_PARENT_INVALID, "Đơn vị cha phải là cấp Tỉnh / Thành phố");
		}
		return parent;
	}

	private static short levelOf(AdministrativeUnitType type) {
		return (type == AdministrativeUnitType.TINH || type == AdministrativeUnitType.THANH_PHO) ? (short) 1
				: (short) 2;
	}

	private static AdminUnitResponse toResponse(AdministrativeUnit unit) {
		return new AdminUnitResponse(unit.getId(), unit.getCode(), unit.getName(), unit.getType(), unit.getLevel(),
				unit.getParentId(), unit.isActive(), unit.getEffectiveFrom());
	}

}
