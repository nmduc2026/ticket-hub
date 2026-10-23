package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.response.OrganizerResponses.BulkApproveResult;
import com.ndz.catalog.dto.response.OrganizerResponses.OrganizerPage;
import com.ndz.catalog.dto.response.OrganizerResponses.ReviewResult;
import com.ndz.catalog.entity.enums.OrganizerStatus;

/** Admin duyệt hồ sơ organizer (overview.md §7.6). */
public interface OrganizerAdminService {

	/** Hồ sơ đã nộp, lọc theo trạng thái nếu có, mới nộp trước. */
	OrganizerPage list(OrganizerStatus status, int page, int size);

	/** @throws com.ndz.shared.exception.BusinessException {@code ORGANIZER_NOT_FOUND}, {@code ORGANIZER_NOT_PENDING} */
	ReviewResult approve(UUID adminId, UUID organizerId);

	/** Lý do bắt buộc; organizer nhận thông báo và email kèm lý do. */
	ReviewResult reject(UUID adminId, UUID organizerId, String reason);

	/** Duyệt nhiều hồ sơ; hồ sơ không ở {@code PENDING} hoặc không tồn tại được bỏ qua và báo lại. */
	BulkApproveResult bulkApprove(UUID adminId, List<UUID> organizerIds);

}
