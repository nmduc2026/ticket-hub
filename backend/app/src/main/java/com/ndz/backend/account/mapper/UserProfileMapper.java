package com.ndz.backend.account.mapper;

import org.springframework.stereotype.Component;

import com.ndz.backend.account.dto.response.MeResponse;
import com.ndz.backend.account.entity.UserProfile;

@Component
public class UserProfileMapper {

	public MeResponse toMeResponse(UserProfile profile) {
		return new MeResponse(profile.getUserId(), profile.getEmail(), profile.getFullName(), profile.getUsername(),
				profile.getRoles(), profile.isEnabled());
	}

}
