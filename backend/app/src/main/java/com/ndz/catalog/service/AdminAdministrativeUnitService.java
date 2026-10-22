package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.request.AdminUnitRequest;
import com.ndz.catalog.dto.response.AdminUnitResponse;

/** Quản lý đơn vị hành chính của Admin (overview.md §11.2, §6.1). */
public interface AdminAdministrativeUnitService {

	/** Mọi đơn vị, kể cả đã tắt (khác danh mục công khai chỉ có đơn vị đang hoạt động). */
	List<AdminUnitResponse> list(Integer level, UUID parentId);

	AdminUnitResponse create(UUID adminId, AdminUnitRequest request);

	/** @throws com.ndz.shared.exception.BusinessException {@code ADMIN_UNIT_NOT_FOUND} */
	AdminUnitResponse update(UUID adminId, UUID id, AdminUnitRequest request);

	/**
	 * Tắt (không xoá cứng); chặn khi còn con đang hoạt động (cấp 1) hoặc còn địa điểm (cấp 2).
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code ADMIN_UNIT_NOT_FOUND}, {@code ADMIN_UNIT_IN_USE}
	 */
	void disable(UUID adminId, UUID id);

}
