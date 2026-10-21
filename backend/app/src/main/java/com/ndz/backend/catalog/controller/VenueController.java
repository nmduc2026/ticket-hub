package com.ndz.backend.catalog.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.catalog.dto.response.VenueResponses.SeatMapResponse;
import com.ndz.backend.catalog.dto.response.VenueResponses.VenueResponse;
import com.ndz.backend.catalog.mapper.VenueMapper;
import com.ndz.backend.catalog.service.VenueQueryService;

/** Địa điểm và sơ đồ ghế để organizer chọn khi tạo sự kiện (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/venues")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
class VenueController {

	private final VenueQueryService service;
	private final VenueMapper mapper;

	VenueController(VenueQueryService service, VenueMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "listVenues", summary = "Danh sách địa điểm")
	@GetMapping
	List<VenueResponse> venues() {
		return service.venues().stream().map(mapper::toVenue).toList();
	}

	@Operation(operationId = "listVenueSeatMaps", summary = "Sơ đồ ghế của một địa điểm, kèm khu và phần tử không bán")
	@GetMapping("/{venueId}/seat-maps")
	List<SeatMapResponse> seatMaps(@PathVariable UUID venueId) {
		return service.seatMaps(venueId).stream().map(mapper::toSeatMap).toList();
	}

}
