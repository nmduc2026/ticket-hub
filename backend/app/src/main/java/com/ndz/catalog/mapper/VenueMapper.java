package com.ndz.catalog.mapper;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.ndz.catalog.dto.response.CatalogViews.ElementRow;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapView;
import com.ndz.catalog.dto.response.CatalogViews.SectionRow;
import com.ndz.catalog.dto.response.CatalogViews.UnitRow;
import com.ndz.catalog.dto.response.CatalogViews.VenueRow;
import com.ndz.catalog.dto.response.UnitResponse;
import com.ndz.catalog.dto.response.VenueResponses.Element;
import com.ndz.catalog.dto.response.VenueResponses.SeatMapResponse;
import com.ndz.catalog.dto.response.VenueResponses.Section;
import com.ndz.catalog.dto.response.VenueResponses.VenueResponse;

/** Chuyển dòng đọc của địa điểm, sơ đồ ghế và danh mục tỉnh / xã sang response; {@code shape_json} thành JSON. */
@Component
public class VenueMapper {

	private final ObjectMapper json;

	VenueMapper(ObjectMapper json) {
		this.json = json;
	}

	public VenueResponse toVenue(VenueRow row) {
		return new VenueResponse(row.id(), row.name(), row.address(), row.wardName(), row.cityName(), row.capacity());
	}

	public SeatMapResponse toSeatMap(SeatMapView view) {
		return new SeatMapResponse(view.map().id(), view.map().name(), view.map().version(), view.map().width(),
				view.map().height(), view.map().locked(), view.sections().stream().map(this::toSection).toList(),
				view.elements().stream().map(this::toElement).toList());
	}

	public UnitResponse toUnit(UnitRow row) {
		return new UnitResponse(row.id(), row.code(), row.name(), row.type(), row.level(), row.parentId());
	}

	private Section toSection(SectionRow row) {
		return new Section(row.id(), row.code(), row.name(), row.seatCount(), json.readTree(row.shapeJson()));
	}

	private Element toElement(ElementRow row) {
		return new Element(row.id(), row.type(), row.label(), json.readTree(row.shapeJson()));
	}

}
