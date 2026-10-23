package com.ndz.account.service;

import com.ndz.catalog.event.OrganizerApproved;

/** Gán role {@code ORGANIZER} khi hồ sơ organizer được duyệt (overview.md §6.6, §7.6). */
public interface OrganizerRoleService {

	/**
	 * Gán role trong Keycloak rồi thêm vào {@code user_profiles.roles}. Idempotent: gửi lại event không lỗi. Keycloak
	 * lỗi thì ném lỗi để event được gửi lại sau.
	 */
	void onOrganizerApproved(OrganizerApproved event);

}
