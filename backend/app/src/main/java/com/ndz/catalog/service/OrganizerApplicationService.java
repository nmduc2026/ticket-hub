package com.ndz.catalog.service;

import java.util.UUID;

import com.ndz.catalog.dto.request.OrganizerApplyRequest;
import com.ndz.catalog.dto.response.OrganizerResponses.Application;
import com.ndz.catalog.dto.response.OrganizerResponses.ApplyResult;

/** Người dùng đăng ký làm organizer và xem trạng thái hồ sơ của chính mình (overview.md §7.6). */
public interface OrganizerApplicationService {

	/**
	 * Nộp hồ sơ lần đầu, hoặc nộp lại sau khi bị từ chối (về {@code PENDING}). Phát {@code OrganizerApplied}.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code APPLICATION_ALREADY_PENDING},
	 *         {@code ALREADY_ORGANIZER}, {@code LEGAL_ID_INVALID}, {@code PHONE_INVALID}, {@code WEBSITE_INVALID}
	 */
	ApplyResult apply(UUID userId, OrganizerApplyRequest request);

	/** Hồ sơ của chính người gọi; chưa nộp thì {@code APPLICATION_NOT_FOUND} (404). */
	Application myApplication(UUID userId);

}
