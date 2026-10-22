package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.request.VenueRequest;
import com.ndz.catalog.dto.response.AdminVenueResponse;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;

/** Quản lý địa điểm của Admin (overview.md §11.2). */
public interface AdminVenueService {

	List<AdminVenueResponse> list();

	/**
	 * @throws com.ndz.shared.exception.BusinessException {@code WARD_LEVEL_INVALID} khi {@code wardId} không trỏ cấp 2
	 */
	AdminVenueResponse create(UUID adminId, VenueRequest request);

	/** @throws com.ndz.shared.exception.BusinessException {@code VENUE_NOT_FOUND}, {@code WARD_LEVEL_INVALID} */
	AdminVenueResponse update(UUID adminId, UUID id, VenueRequest request);

	/** Mỗi id xử lý riêng; chỉ xoá được địa điểm chưa có sự kiện ({@code VENUE_IN_USE}). */
	BulkResult bulkDelete(UUID adminId, List<UUID> ids);

}
