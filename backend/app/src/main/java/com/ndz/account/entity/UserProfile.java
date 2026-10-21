package com.ndz.account.entity;

import java.time.Instant;
import java.time.LocalDate;
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

	@Column(name = "avatar_url")
	private String avatarUrl;

	private String phone;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "province_id")
	private UUID provinceId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected UserProfile() {
	}

	/** Khách sửa thông tin app giữ (§6.6); email, tên, mật khẩu sửa ở Keycloak. */
	public void changeDetails(String phone, LocalDate dateOfBirth, UUID provinceId, Instant now) {
		this.phone = phone;
		this.dateOfBirth = dateOfBirth;
		this.provinceId = provinceId;
		this.updatedAt = now;
	}

	/** Đổi ảnh đại diện; trả về địa chỉ ảnh cũ (có thể null) để xoá file cũ. */
	public String changeAvatar(String avatarUrl, Instant now) {
		String previous = this.avatarUrl;
		this.avatarUrl = avatarUrl;
		this.updatedAt = now;
		return previous;
	}

	/**
	 * Xoá tài khoản: ẩn danh hoá mọi thông tin cá nhân, giữ dòng (và {@code user_id}) để booking, vé cũ vẫn đối soát
	 * được. Trả về địa chỉ ảnh đại diện cũ (có thể null).
	 */
	public String anonymize(Instant now) {
		String previousAvatar = this.avatarUrl;
		this.email = null;
		this.fullName = null;
		this.username = null;
		this.avatarUrl = null;
		this.phone = null;
		this.dateOfBirth = null;
		this.provinceId = null;
		this.enabled = false;
		this.deletedAt = now;
		this.updatedAt = now;
		return previousAvatar;
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

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public String getPhone() {
		return phone;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public UUID getProvinceId() {
		return provinceId;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
