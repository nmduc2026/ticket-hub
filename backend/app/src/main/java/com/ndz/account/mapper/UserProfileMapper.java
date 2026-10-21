package com.ndz.account.mapper;

import org.springframework.stereotype.Component;

import com.ndz.account.dto.response.MeResponse;
import com.ndz.account.entity.UserProfile;

@Component
public class UserProfileMapper {

	public MeResponse toMeResponse(UserProfile profile) {
		return new MeResponse(profile.getUserId(), profile.getEmail(), profile.getFullName(), profile.getUsername(),
				profile.getRoles(), profile.isEnabled());
	}

}
