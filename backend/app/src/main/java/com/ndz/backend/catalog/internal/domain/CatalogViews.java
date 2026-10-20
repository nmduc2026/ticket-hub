package com.ndz.backend.catalog.internal.domain;

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

	public record OrganizerInfo(String status, boolean payoutReady) {
	}

	public record VenueInfo(String name, String cityName) {
	}

}
