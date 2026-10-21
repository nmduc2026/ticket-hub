package com.ndz.backend.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.backend.account.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID>, UserProfileRepositoryCustom {
}
