package com.ndz.backend.catalog.controller;

import com.ndz.backend.catalog.dto.request.EventRequest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ndz.backend.catalog.service.EventBannerService;
import com.ndz.backend.catalog.service.EventLifecycleService;
import com.ndz.backend.catalog.service.OrganizerEventService;
import com.ndz.backend.catalog.dto.request.EventFilter;
import com.ndz.backend.catalog.dto.response.EventResponses.Assignment;
import com.ndz.backend.catalog.entity.enums.EventStatus;
import com.ndz.backend.catalog.dto.response.EventResponses.Detail;
import com.ndz.backend.catalog.dto.response.EventResponses.EventPage;
import com.ndz.backend.catalog.dto.response.EventResponses.Tier;
import com.ndz.backend.catalog.dto.request.TierRequests.AssignmentsRequest;
import com.ndz.backend.catalog.dto.request.TierRequests.TierRequest;
import com.ndz.backend.shared.util.CurrentUser;

/**
 * API sự kiện của organizer (overview.md §11.2). Controller chỉ nhận request, gọi service và dựng response;
 * quyền sở hữu được kiểm trong service.
 */
@RestController
@RequestMapping("/api/v1/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerEventController {

	private final OrganizerEventService service;
	private final EventBannerService banners;
	private final EventLifecycleService lifecycle;

	OrganizerEventController(OrganizerEventService service, EventBannerService banners,
			EventLifecycleService lifecycle) {
		this.service = service;
		this.banners = banners;
		this.lifecycle = lifecycle;
	}

	@Operation(operationId = "listOrganizerEvents", summary = "Danh sách sự kiện của tôi")
	@GetMapping
	EventPage list(@RequestParam(required = false) Set<EventStatus> status,
			@RequestParam(required = false) UUID venueId, @RequestParam(required = false, name = "q") String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(CurrentUser.require().id(), new EventFilter(status, venueId, query), page, size);
	}

	@Operation(operationId = "getOrganizerEvent", summary = "Chi tiết sự kiện của tôi")
	@ApiResponse(responseCode = "200", description = "Chi tiết sự kiện",
			content = @Content(schema = @Schema(implementation = Detail.class)))
	@ApiResponse(responseCode = "404", description = "Không có hoặc không phải sự kiện của bạn (EVENT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{eventId}")
	Detail get(@PathVariable UUID eventId) {
		return service.get(CurrentUser.require().id(), eventId);
	}

	@Operation(operationId = "createOrganizerEvent", summary = "Tạo sự kiện nháp")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Detail create(@Valid @RequestBody EventRequest request) {
		return service.create(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "updateOrganizerEvent", summary = "Sửa sự kiện",
			description = "Sau publish chỉ đổi được tiêu đề, mô tả, danh mục; đổi thông tin bán vé trả EVENT_LOCKED (BR-14)")
	@ApiResponse(responseCode = "200", description = "Chi tiết sự kiện",
			content = @Content(schema = @Schema(implementation = Detail.class)))
	@ApiResponse(responseCode = "409", description = "EVENT_LOCKED",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{eventId}")
	Detail update(@PathVariable UUID eventId, @Valid @RequestBody EventRequest request) {
		return service.update(CurrentUser.require().id(), eventId, request);
	}

	@Operation(operationId = "publishOrganizerEvent", summary = "Publish sự kiện",
			description = "Kiểm BR-05 (organizer đã duyệt và nhận được tiền), chốt mức phí nền tảng (BR-06), khoá thông tin bán vé (BR-14)")
	@ApiResponse(responseCode = "200", description = "Chi tiết sự kiện",
			content = @Content(schema = @Schema(implementation = Detail.class)))
	@ApiResponse(responseCode = "409", description = "PUBLISH_NOT_ALLOWED (kèm reasons) hoặc INVALID_EVENT_TRANSITION",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "PUBLISH_INCOMPLETE (kèm missing) hoặc INVALID_EVENT_TIMES",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{eventId}/publish")
	Detail publish(@PathVariable UUID eventId) {
		return lifecycle.publish(CurrentUser.require().id(), eventId);
	}

	@Operation(operationId = "uploadEventBanner", summary = "Tải ảnh banner",
			description = "jpg, png hoặc webp, tối đa 2 MB; sửa được cả sau publish (thông tin hiển thị)")
	@ApiResponse(responseCode = "200", description = "Chi tiết sự kiện",
			content = @Content(schema = @Schema(implementation = Detail.class)))
	@ApiResponse(responseCode = "422", description = "UNSUPPORTED_IMAGE_TYPE, IMAGE_TOO_LARGE hoặc EMPTY_FILE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(path = "/{eventId}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Detail uploadBanner(@PathVariable UUID eventId, @RequestPart("file") MultipartFile file) throws IOException {
		return banners.upload(CurrentUser.require().id(), eventId, file.getBytes());
	}

	// ---- Hạng vé ----

	@Operation(operationId = "listTicketTiers", summary = "Hạng vé của sự kiện")
	@GetMapping("/{eventId}/ticket-tiers")
	List<Tier> tiers(@PathVariable UUID eventId) {
		return service.tiers(CurrentUser.require().id(), eventId);
	}

	@Operation(operationId = "createTicketTier", summary = "Thêm hạng vé (chỉ khi còn là nháp)")
	@PostMapping("/{eventId}/ticket-tiers")
	@ResponseStatus(HttpStatus.CREATED)
	Tier createTier(@PathVariable UUID eventId, @Valid @RequestBody TierRequest request) {
		return service.createTier(CurrentUser.require().id(), eventId, request);
	}

	@Operation(operationId = "updateTicketTier", summary = "Sửa hạng vé (chỉ khi còn là nháp)")
	@PutMapping("/{eventId}/ticket-tiers/{tierId}")
	Tier updateTier(@PathVariable UUID eventId, @PathVariable UUID tierId, @Valid @RequestBody TierRequest request) {
		return service.updateTier(CurrentUser.require().id(), eventId, tierId, request);
	}

	@Operation(operationId = "deleteTicketTier", summary = "Xoá hạng vé và các khu đã gán cho nó (chỉ khi còn là nháp)")
	@DeleteMapping("/{eventId}/ticket-tiers/{tierId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTier(@PathVariable UUID eventId, @PathVariable UUID tierId) {
		service.deleteTier(CurrentUser.require().id(), eventId, tierId);
	}

	// ---- Gán hạng vé cho khu ghế ----

	@Operation(operationId = "replaceTierSections", summary = "Gán hạng vé cho khu ghế (thay toàn bộ, chỉ khi còn là nháp)")
	@PutMapping("/{eventId}/tier-sections")
	List<Assignment> replaceAssignments(@PathVariable UUID eventId,
			@Valid @RequestBody AssignmentsRequest request) {
		return service.replaceAssignments(CurrentUser.require().id(), eventId, request);
	}

}
