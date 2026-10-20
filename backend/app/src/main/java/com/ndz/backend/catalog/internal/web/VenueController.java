package com.ndz.backend.catalog.internal.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.ndz.backend.catalog.internal.application.VenueQueryService;
import com.ndz.backend.catalog.internal.application.VenueQueryService.SeatMapView;
import com.ndz.backend.catalog.internal.domain.CatalogViews.ElementRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.SectionRow;
import com.ndz.backend.catalog.internal.domain.CatalogViews.VenueRow;

/** Địa điểm và sơ đồ ghế để organizer chọn khi tạo sự kiện (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/venues")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
class VenueController {

	private final VenueQueryService service;
	private final ObjectMapper json;

	VenueController(VenueQueryService service, ObjectMapper json) {
		this.service = service;
		this.json = json;
	}

	@Operation(operationId = "listVenues", summary = "Danh sách địa điểm")
	@GetMapping
	List<VenueResponse> venues() {
		return service.venues().stream().map(VenueResponse::from).toList();
	}

	@Operation(operationId = "listVenueSeatMaps", summary = "Sơ đồ ghế của một địa điểm, kèm khu và phần tử không bán")
	@GetMapping("/{venueId}/seat-maps")
	List<SeatMapResponse> seatMaps(@PathVariable UUID venueId) {
		return service.seatMaps(venueId).stream().map(view -> SeatMapResponse.from(view, json)).toList();
	}

	@Schema(name = "Venue")
	record VenueResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String wardName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int capacity) {

		static VenueResponse from(VenueRow row) {
			return new VenueResponse(row.id(), row.name(), row.address(), row.wardName(), row.cityName(),
					row.capacity());
		}

	}

	@Schema(name = "SeatMap")
	record SeatMapResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int width,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int height,
			@Schema(description = "true khi đã có sự kiện publish dùng: bất biến",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean locked,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Section> sections,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Element> elements) {

		static SeatMapResponse from(SeatMapView view, ObjectMapper json) {
			return new SeatMapResponse(view.map().id(), view.map().name(), view.map().version(), view.map().width(),
					view.map().height(), view.map().locked(),
					view.sections().stream().map(section -> Section.from(section, json)).toList(),
					view.elements().stream().map(element -> Element.from(element, json)).toList());
		}

	}

	@Schema(name = "SeatMapSection")
	record Section(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Số ghế của khu", requiredMode = Schema.RequiredMode.REQUIRED) int seatCount,
			@Schema(description = "Tham số hình học của khu (GRID / IMPORTED), nguyên văn từ shape_json",
					requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {

		static Section from(SectionRow row, ObjectMapper json) {
			return new Section(row.id(), row.code(), row.name(), row.seatCount(), json.readTree(row.shapeJson()));
		}

	}

	@Schema(name = "SeatMapElement")
	record Element(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(description = "STAGE, AISLE, LABEL hoặc AREA", requiredMode = Schema.RequiredMode.REQUIRED) String type,
			String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) JsonNode shape) {

		static Element from(ElementRow row, ObjectMapper json) {
			return new Element(row.id(), row.type(), row.label(), json.readTree(row.shapeJson()));
		}

	}

}
