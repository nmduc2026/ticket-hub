package com.ndz.backend.catalog.internal.web;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.ndz.backend.catalog.internal.application.EventBannerService;
import com.ndz.backend.catalog.internal.application.OrganizerEventService;
import com.ndz.backend.catalog.internal.application.OrganizerEventService.Assignment;
import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventFilter;
import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventRow;
import com.ndz.backend.catalog.internal.domain.EventStatus;
import com.ndz.backend.catalog.internal.web.EventResponses.Detail;
import com.ndz.backend.catalog.internal.web.EventResponses.EventPage;
import com.ndz.backend.catalog.internal.web.EventResponses.Tier;
import com.ndz.backend.catalog.internal.web.EventResponses.Summary;
import com.ndz.backend.catalog.internal.web.TierRequests.AssignmentsRequest;
import com.ndz.backend.catalog.internal.web.TierRequests.TierRequest;
import com.ndz.backend.shared.CurrentUser;

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

	OrganizerEventController(OrganizerEventService service, EventBannerService banners) {
		this.service = service;
		this.banners = banners;
	}

	@Operation(operationId = "listOrganizerEvents", summary = "Danh sách sự kiện của tôi")
	@GetMapping
	EventPage list(@RequestParam(required = false) Set<EventStatus> status,
			@RequestParam(required = false) UUID venueId, @RequestParam(required = false, name = "q") String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		Page<EventRow> found = service.list(CurrentUser.require().id(), new EventFilter(status, venueId, query), page,
				size);
		return new EventPage(found.getContent().stream().map(Summary::from).toList(), found.getTotalElements(),
				found.getNumber(), found.getSize());
	}

	@Operation(operationId = "getOrganizerEvent", summary = "Chi tiết sự kiện của tôi")
	@ApiResponse(responseCode = "404", description = "Không có hoặc không phải sự kiện của bạn (EVENT_NOT_FOUND)")
	@GetMapping("/{eventId}")
	Detail get(@PathVariable UUID eventId) {
		return Detail.from(service.get(CurrentUser.require().id(), eventId));
	}

	@Operation(operationId = "createOrganizerEvent", summary = "Tạo sự kiện nháp")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Detail create(@Valid @RequestBody EventRequest request) {
		return Detail.from(service.create(CurrentUser.require().id(), request.toContent()));
	}

	@Operation(operationId = "updateOrganizerEvent", summary = "Sửa sự kiện",
			description = "Sau publish chỉ đổi được tiêu đề, mô tả, danh mục; đổi thông tin bán vé trả EVENT_LOCKED (BR-14)")
	@ApiResponse(responseCode = "409", description = "EVENT_LOCKED")
	@PutMapping("/{eventId}")
	Detail update(@PathVariable UUID eventId, @Valid @RequestBody EventRequest request) {
		return Detail.from(service.update(CurrentUser.require().id(), eventId, request.toContent()));
	}

	@Operation(operationId = "uploadEventBanner", summary = "Tải ảnh banner",
			description = "jpg, png hoặc webp, tối đa 2 MB; sửa được cả sau publish (thông tin hiển thị)")
	@ApiResponse(responseCode = "422", description = "UNSUPPORTED_IMAGE_TYPE, IMAGE_TOO_LARGE hoặc EMPTY_FILE")
	@PostMapping(path = "/{eventId}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Detail uploadBanner(@PathVariable UUID eventId, @RequestPart("file") MultipartFile file) throws IOException {
		return Detail.from(banners.upload(CurrentUser.require().id(), eventId, file.getBytes()));
	}

	// ---- Hạng vé ----

	@Operation(operationId = "listTicketTiers", summary = "Hạng vé của sự kiện")
	@GetMapping("/{eventId}/ticket-tiers")
	List<Tier> tiers(@PathVariable UUID eventId) {
		return service.tiers(CurrentUser.require().id(), eventId).stream().map(Tier::from).toList();
	}

	@Operation(operationId = "createTicketTier", summary = "Thêm hạng vé (chỉ khi còn là nháp)")
	@PostMapping("/{eventId}/ticket-tiers")
	@ResponseStatus(HttpStatus.CREATED)
	Tier createTier(@PathVariable UUID eventId, @Valid @RequestBody TierRequest request) {
		return Tier.from(service.createTier(CurrentUser.require().id(), eventId, request.name(), request.priceMinor()));
	}

	@Operation(operationId = "updateTicketTier", summary = "Sửa hạng vé (chỉ khi còn là nháp)")
	@PutMapping("/{eventId}/ticket-tiers/{tierId}")
	Tier updateTier(@PathVariable UUID eventId, @PathVariable UUID tierId, @Valid @RequestBody TierRequest request) {
		return Tier.from(
				service.updateTier(CurrentUser.require().id(), eventId, tierId, request.name(), request.priceMinor()));
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
	List<EventResponses.Assignment> replaceAssignments(@PathVariable UUID eventId,
			@Valid @RequestBody AssignmentsRequest request) {
		List<Assignment> assignments = request.assignments().stream()
				.map(item -> new Assignment(item.sectionId(), item.tierId())).toList();
		return service.replaceAssignments(CurrentUser.require().id(), eventId, assignments).stream()
				.map(EventResponses.Assignment::from).toList();
	}

}
