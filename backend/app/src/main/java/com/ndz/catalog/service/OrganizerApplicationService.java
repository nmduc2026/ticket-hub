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

	/**
	 * Sửa hồ sơ công khai và pháp lý (#27). Đổi tên pháp lý / mã số thuế hoặc CCCD / loại hình đưa hồ sơ về
	 * {@code PENDING} chờ Admin duyệt lại; các trường khác đổi ngay.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code ORGANIZER_STATE_INVALID} (chưa là organizer đang
	 *         hoạt động), {@code LEGAL_ID_INVALID}, {@code PHONE_INVALID}, {@code WEBSITE_INVALID}
	 */
	Application updateProfile(UUID userId, OrganizerApplyRequest request);

}
