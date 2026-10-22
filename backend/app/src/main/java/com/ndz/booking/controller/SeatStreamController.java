package com.ndz.booking.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ndz.booking.service.SeatStreamService;

/** Trạng thái ghế realtime cho khách chưa đăng nhập cũng xem được (overview.md §12). */
@RestController
@RequestMapping("/api/v1/events/{eventId}/seats")
class SeatStreamController {

	private final SeatStreamService streams;

	SeatStreamController(SeatStreamService streams) {
		this.streams = streams;
	}

	@Operation(operationId = "streamSeats", summary = "Luồng SSE thay đổi trạng thái ghế",
			description = "event: seats {changes: [{seatId, status}]} với status AVAILABLE | HELD | SOLD; heartbeat 15 giây. "
					+ "Chỉ mang thay đổi: tải snapshot GET /events/{id}/seats sau khi nối, và tải lại khi nối lại.")
	@ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream(@PathVariable UUID eventId) {
		return streams.open(eventId);
	}

}
