package com.ndz.account.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

/**
 * Bản sao hồ sơ Keycloak (overview.md §6.6). Đồng bộ từ token ghi bằng upsert nguyên tử ở
 * {@code UserProfileRepositoryImpl} để hai request đầu tiên đồng thời không đụng khoá chính.
 */
@Entity
@Table(schema = "account", name = "user_profiles")
@AuditTable(schema = "account", value = "user_profiles_aud")
@DynamicUpdate
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

	@Audited
	private boolean enabled;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected UserProfile() {
	}

	/** Khoá / mở khoá tài khoản (Admin); Envers ghi revision. Chỉ cột đổi được ghi (@DynamicUpdate) nên không đè lên upsert lúc đăng nhập. */
	public void changeEnabled(boolean enabled, Instant now) {
		this.enabled = enabled;
		this.updatedAt = now;
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
