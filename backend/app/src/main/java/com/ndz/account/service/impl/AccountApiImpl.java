package com.ndz.account.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.api.AccountApi;
import com.ndz.account.repository.UserProfileRepository;

/** Cài đặt {@link AccountApi} trong cùng process (giai đoạn modular monolith). */
@Service
class AccountApiImpl implements AccountApi {

	private final UserProfileRepository profiles;

	AccountApiImpl(UserProfileRepository profiles) {
		this.profiles = profiles;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserContact> usersWithRole(String role) {
		return profiles.findEnabledByRole(role).stream()
				.map(profile -> new UserContact(profile.getUserId(), profile.getEmail(), profile.getFullName()))
				.toList();
	}

}
