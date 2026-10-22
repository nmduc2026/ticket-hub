package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.request.SeatMapRequest;
import com.ndz.catalog.dto.response.AdminSeatMapResponses.Summary;
import com.ndz.catalog.dto.response.VenueResponses.SeatMapResponse;

/** Quản lý sơ đồ ghế của Admin (màn #17, ADR 0030, overview.md §6.1, §11.2). */
public interface AdminSeatMapService {

	List<Summary> list(UUID venueId);

	SeatMapResponse get(UUID id);

	/** Tạo sơ đồ {@code version} 1 của địa điểm; ghế sinh từ tham số các khu {@code GRID}. */
	SeatMapResponse create(UUID adminId, SeatMapRequest request);

	/**
	 * Thay toàn bộ khu và phần tử của sơ đồ rồi sinh lại ghế. Khu giữ nguyên {@code id} theo {@code code}, nên gán hạng
	 * vé ({@code tier_sections}) còn nguyên.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code SEAT_MAP_LOCKED} nếu sơ đồ đã khoá,
	 *         {@code SEAT_MAP_BEING_EDITED} nếu Admin khác đang lưu, {@code SECTION_IN_USE} nếu xoá khu đã gán hạng vé
	 */
	SeatMapResponse update(UUID adminId, UUID id, SeatMapRequest request);

	/** Bản sao {@code version} mới (chưa khoá) của sơ đồ; sự kiện đã publish vẫn dùng bản cũ. */
	SeatMapResponse copy(UUID adminId, UUID id);

	/** Xoá sơ đồ chưa khoá và chưa sự kiện nào dùng. */
	void delete(UUID adminId, UUID id);

}
