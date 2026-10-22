package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.request.EventFilter;
import com.ndz.catalog.dto.request.EventRequest;
import com.ndz.catalog.dto.request.TierRequests.AssignmentsRequest;
import com.ndz.catalog.dto.request.TierRequests.TierRequest;
import com.ndz.catalog.dto.response.EventResponses.Assignment;
import com.ndz.catalog.dto.response.EventResponses.Detail;
import com.ndz.catalog.dto.response.EventResponses.EventPage;
import com.ndz.catalog.dto.response.EventResponses.Tier;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;

/**
 * Use case của organizer trên sự kiện của chính họ (overview.md §11.2, §13.3). Sự kiện của người khác cho kết quả
 * "không tìm thấy" như sự kiện không tồn tại (chống IDOR).
 */
public interface OrganizerEventService {

	Detail create(UUID organizerId, EventRequest request);

	Detail update(UUID organizerId, UUID eventId, EventRequest request);

	Detail get(UUID organizerId, UUID eventId);

	EventPage list(UUID organizerId, EventFilter filter, int page, int size);

	List<Tier> tiers(UUID organizerId, UUID eventId);

	Tier createTier(UUID organizerId, UUID eventId, TierRequest request);

	Tier updateTier(UUID organizerId, UUID eventId, UUID tierId, TierRequest request);

	void deleteTier(UUID organizerId, UUID eventId, UUID tierId);

	/** Thay toàn bộ cách gán: danh sách gửi lên là trạng thái mới; khu không có trong danh sách thì không bán. */
	List<Assignment> replaceAssignments(UUID organizerId, UUID eventId, AssignmentsRequest request);

	/**
	 * Nhân bản thành bản nháp mới: chép thông tin, hạng vé và cách gán khu, không chép giờ mở bán (#12, #14).
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code EVENT_NOT_FOUND}
	 */
	Detail duplicate(UUID organizerId, UUID eventId);

	/** Mỗi id xử lý riêng, không ảnh hưởng id khác. */
	BulkResult bulkDuplicate(UUID organizerId, List<UUID> eventIds);

}
