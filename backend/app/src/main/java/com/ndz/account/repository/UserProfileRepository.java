package com.ndz.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.account.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID>, UserProfileRepositoryCustom {
}
