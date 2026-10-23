package com.ndz.account.service;

import java.util.List;
import java.util.UUID;

import com.ndz.account.dto.request.ProfileRequests.UpdateProfile;
import com.ndz.account.dto.response.ProfileResponses.AvatarResponse;
import com.ndz.account.dto.response.ProfileResponses.ProfileResponse;
import com.ndz.account.dto.response.ProfileResponses.SessionResponse;
import com.ndz.shared.util.CurrentUser;

/**
 * Hồ sơ cá nhân của người đang đăng nhập (overview.md §6.6, màn #19): thông tin app giữ, ảnh đại diện, phiên đăng
 * nhập và xoá tài khoản qua Keycloak Admin API.
 */
public interface MyProfileService {

	ProfileResponse get(UUID userId);

	/** {@code PHONE_INVALID}, {@code DATE_OF_BIRTH_INVALID}, {@code PROVINCE_NOT_FOUND} khi dữ liệu sai. */
	ProfileResponse update(UUID userId, UpdateProfile request);

	/** Ảnh jpg / png / webp, ≤ 2 MB; xoá ảnh cũ sau khi lưu ảnh mới. */
	AvatarResponse uploadAvatar(UUID userId, byte[] content);

	/** Các phiên đăng nhập; phiên của token đang gọi có {@code current = true}. */
	List<SessionResponse> sessions(CurrentUser user);

	/** Kết thúc một phiên khác của chính mình; phiên hiện tại → {@code CANNOT_END_CURRENT_SESSION}. */
	void endSession(CurrentUser user, String sessionId);

	/**
	 * Xoá tài khoản: xoá user ở Keycloak, ẩn danh hoá hồ sơ; booking và vé cũ giữ nguyên để đối soát. Còn vé của sự
	 * kiện chưa diễn → {@code ACCOUNT_HAS_UPCOMING_TICKETS}; email gõ lại không khớp → {@code CONFIRM_EMAIL_MISMATCH}.
	 */
	void deleteAccount(UUID userId, String confirmEmail);

}
