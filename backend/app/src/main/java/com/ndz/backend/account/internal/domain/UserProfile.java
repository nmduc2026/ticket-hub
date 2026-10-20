package com.ndz.backend.account.internal.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản sao hồ sơ Keycloak (overview.md §6.6). Chỉ đọc qua JPA; ghi bằng upsert nguyên tử ở
 * {@code UserProfileWriter} để hai request đầu tiên đồng thời không đụng khoá chính.
 */
@Entity
@Table(schema = "account", name = "user_profiles")
public class UserProfile {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	private String email;

	@Column(name = "full_name")
	private String fullName;

	private String username;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> roles;

	private boolean enabled;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected UserProfile() {
	}

	public UUID getUserId() {
		return userId;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public String getUsername() {
		return username;
	}

	public List<String> getRoles() {
		return roles;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

}
