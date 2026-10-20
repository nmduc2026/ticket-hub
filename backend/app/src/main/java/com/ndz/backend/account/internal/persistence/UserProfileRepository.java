package com.ndz.backend.account.internal.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.backend.account.internal.domain.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
