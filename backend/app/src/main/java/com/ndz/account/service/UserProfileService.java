package com.ndz.account.service;

import java.util.UUID;

import com.ndz.account.entity.UserProfile;
import com.ndz.shared.util.CurrentUser;

/** Hồ sơ người dùng, đồng bộ từ claim của Keycloak (overview.md §6.6, ADR 0016). */
public interface UserProfileService {

	/** Tạo hồ sơ ở request đầu tiên; sau đó chỉ ghi khi claim đổi hoặc có lần đăng nhập mới. */
	void sync(CurrentUser user);

	/** Hồ sơ của người dùng; ném {@code PROFILE_NOT_FOUND} nếu chưa có. */
	UserProfile get(UUID userId);

}
