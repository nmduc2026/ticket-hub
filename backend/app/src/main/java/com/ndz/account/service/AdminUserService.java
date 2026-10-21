package com.ndz.account.service;

import java.util.UUID;

import com.ndz.account.dto.response.AdminUserResponses.LockResult;
import com.ndz.account.dto.response.AdminUserResponses.UserDetail;
import com.ndz.account.dto.response.AdminUserResponses.UserPage;

/** Admin tìm, xem và khoá / mở khoá người dùng (overview.md §6.6). */
public interface AdminUserService {

	/** Tìm theo tên / email / username (tuỳ chọn) và role (tuỳ chọn), người mới trước. */
	UserPage list(String query, String role, int page, int size);

	/**
	 * Hồ sơ cùng đơn hàng, vé, yêu cầu hoàn tiền và (với organizer) sự kiện; dữ liệu của module khác lấy qua interface
	 * công khai của module đó.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code USER_NOT_FOUND}
	 */
	UserDetail detail(UUID userId);

	/**
	 * Khoá tài khoản: tắt đăng nhập và đăng xuất mọi session ở Keycloak, rồi cập nhật hồ sơ. Access token đã cấp còn
	 * dùng được tối đa 5 phút. Idempotent.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code CANNOT_LOCK_SELF}, {@code USER_NOT_FOUND},
	 *         {@code IDENTITY_PROVIDER_UNAVAILABLE}
	 */
	LockResult lock(UUID adminId, UUID userId);

	LockResult unlock(UUID userId);

}
