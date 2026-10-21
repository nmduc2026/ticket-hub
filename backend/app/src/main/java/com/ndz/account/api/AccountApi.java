package com.ndz.account.api;

import java.util.List;
import java.util.UUID;

/**
 * Interface công khai của module Account cho module khác gọi đồng bộ (ADR 0018). Khi tách service, thay cài đặt bằng
 * lời gọi API nội bộ; code gọi giữ nguyên.
 */
public interface AccountApi {

	/** Người dùng còn hoạt động có role này (vd. {@code ADMIN}), để Notification tìm người nhận thông báo vận hành. */
	List<UserContact> usersWithRole(String role);

	record UserContact(UUID userId, String email, String fullName) {
	}

}
