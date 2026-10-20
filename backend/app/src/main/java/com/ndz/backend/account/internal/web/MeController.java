package com.ndz.backend.account.internal.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.account.internal.application.UserProfileService;
import com.ndz.backend.account.internal.domain.UserProfile;
import com.ndz.backend.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final UserProfileService profiles;

	MeController(UserProfileService profiles) {
		this.profiles = profiles;
	}

	/** Hồ sơ của người đang đăng nhập (đã được đồng bộ ở interceptor trước khi vào đây). */
	@GetMapping
	MeResponse me() {
		return MeResponse.from(profiles.get(CurrentUser.require().id()));
	}

	/** {@code roles} là role hiện có trong hồ sơ: CUSTOMER, ORGANIZER hoặc ADMIN. */
	record MeResponse(UUID id, String email, String fullName, String username, List<String> roles, boolean enabled) {

		static MeResponse from(UserProfile profile) {
			return new MeResponse(profile.getUserId(), profile.getEmail(), profile.getFullName(),
					profile.getUsername(), profile.getRoles(), profile.isEnabled());
		}

	}

}
