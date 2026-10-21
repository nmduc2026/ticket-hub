package com.ndz.backend.account.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ndz.backend.account.service.UserProfileService;
import com.ndz.backend.shared.util.CurrentUser;

/** Đồng bộ hồ sơ ở mọi request đã xác thực tới `/api/**` (overview.md §6.6, ADR 0016). */
@Component
class ProfileSyncInterceptor implements HandlerInterceptor {

	private final UserProfileService profiles;

	ProfileSyncInterceptor(UserProfileService profiles) {
		this.profiles = profiles;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		CurrentUser.current().ifPresent(profiles::sync);
		return true;
	}

}
