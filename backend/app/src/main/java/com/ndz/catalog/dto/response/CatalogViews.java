package com.ndz.catalog.dto.response;

import java.util.List;
import java.util.UUID;

/** Giá trị chỉ đọc của địa điểm, sơ đồ ghế và tổ chức (do Admin quản lý, organizer chỉ chọn). */
public final class CatalogViews {

	private CatalogViews() {
	}

	public record VenueRow(UUID id, String name, String address, String wardName, String cityName, int capacity) {
	}

	public record SeatMapRow(UUID id, UUID venueId, String name, int version, int width, int height, boolean locked) {
	}

	public record SectionRow(UUID id, UUID seatMapId, String code, String name, String shapeJson, int seatCount) {
	}

	public record ElementRow(UUID id, UUID seatMapId, String type, String label, String shapeJson) {
	}

	public record UnitRow(UUID id, String code, String name, String type, int level, UUID parentId) {
	}

	public record OrganizerInfo(String status, boolean payoutReady) {
	}

	/** Một sơ đồ ghế kèm các khu và phần tử không bán để frontend vẽ. */
	public record SeatMapView(SeatMapRow map, List<SectionRow> sections, List<ElementRow> elements) {
	}

	public record VenueInfo(String name, UUID cityId, String cityName) {
	}

}
