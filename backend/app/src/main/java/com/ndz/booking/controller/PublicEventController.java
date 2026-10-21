package com.ndz.booking.controller;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.ndz.booking.mapper.PublicEventMapper;
import com.ndz.booking.service.PublicEventService;
import com.ndz.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.booking.dto.response.PublicEventResponses.Detail;
import com.ndz.booking.dto.response.PublicEventResponses.Page;
import com.ndz.booking.dto.response.PublicEventResponses.Seats;

/** API công khai cho khách (kể cả chưa đăng nhập): tìm sự kiện, chi tiết, sơ đồ ghế kèm trạng thái (overview.md §11.2). */
@RestController
@RequestMapping("/api/v1/events")
class PublicEventController {

	private final PublicEventService service;
	private final PublicEventMapper mapper;

	PublicEventController(PublicEventService service, PublicEventMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Operation(operationId = "searchEvents", summary = "Tìm và lọc sự kiện",
			description = "Chỉ sự kiện đang chờ mở bán, đang bán hoặc tạm dừng; sắp theo giờ diễn")
	@GetMapping
	Page search(@RequestParam(required = false, name = "q") String query,
			@RequestParam(required = false) UUID cityId, @RequestParam(required = false) String category,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
		return mapper.toPage(service.search(new SearchCriteria(query, cityId, category, from, to), page, size));
	}

	@Operation(operationId = "getEvent", summary = "Chi tiết sự kiện, hạng vé và còn vé hay không")
	@ApiResponse(responseCode = "200", description = "Chi tiết sự kiện",
			content = @Content(schema = @Schema(implementation = Detail.class)))
	@ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND")
	@GetMapping("/{eventId}")
	Detail get(@PathVariable UUID eventId) {
		return mapper.toDetail(service.detail(eventId));
	}

	@Operation(operationId = "getEventSeats", summary = "Sơ đồ ghế kèm trạng thái",
			description = "Ghế của khu không gán hạng vé có trạng thái NOT_FOR_SALE (khu màu xám, không chọn được)")
	@ApiResponse(responseCode = "200", description = "Sơ đồ ghế",
			content = @Content(schema = @Schema(implementation = Seats.class)))
	@ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND")
	@GetMapping("/{eventId}/seats")
	Seats seats(@PathVariable UUID eventId) {
		return mapper.toSeats(service.seats(eventId));
	}

}
