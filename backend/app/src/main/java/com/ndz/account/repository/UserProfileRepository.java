package com.ndz.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID>, UserProfileRepositoryCustom {

	/**
	 * Tìm theo tên / email / username (mẫu {@code LIKE} đã viết thường và escape bằng {@code !}) và role; cả hai điều
	 * kiện đều tuỳ chọn.
	 */
	@Query(value = """
			SELECT * FROM account.user_profiles
			WHERE (CAST(:pattern AS TEXT) IS NULL OR lower(email) LIKE :pattern ESCAPE '!'
			       OR lower(full_name) LIKE :pattern ESCAPE '!' OR lower(username) LIKE :pattern ESCAPE '!')
			  AND (CAST(:role AS TEXT) IS NULL OR CAST(:role AS TEXT) = ANY (roles))
			ORDER BY created_at DESC, user_id
			""", countQuery = """
			SELECT count(*) FROM account.user_profiles
			WHERE (CAST(:pattern AS TEXT) IS NULL OR lower(email) LIKE :pattern ESCAPE '!'
			       OR lower(full_name) LIKE :pattern ESCAPE '!' OR lower(username) LIKE :pattern ESCAPE '!')
			  AND (CAST(:role AS TEXT) IS NULL OR CAST(:role AS TEXT) = ANY (roles))
			""", nativeQuery = true)
	Page<UserProfile> search(@Param("pattern") String pattern, @Param("role") String role, Pageable pageable);

	/** Hồ sơ có role này, dùng để tìm người nhận thông báo vận hành (vd. mọi Admin). */
	@Query(value = "SELECT * FROM account.user_profiles WHERE :role = ANY (roles) AND enabled ORDER BY user_id",
			nativeQuery = true)
	List<UserProfile> findEnabledByRole(@Param("role") String role);

	/** Thêm role vào hồ sơ nếu chưa có; không có hồ sơ (chưa đăng nhập lần nào) thì không làm gì. */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE account.user_profiles SET roles = array_append(roles, :role), updated_at = :now
			WHERE user_id = :userId AND NOT (:role = ANY (roles))
			""", nativeQuery = true)
	int addRole(@Param("userId") UUID userId, @Param("role") String role, @Param("now") Instant now);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE account.user_profiles SET enabled = :enabled, updated_at = :now WHERE user_id = :userId",
			nativeQuery = true)
	int setEnabled(@Param("userId") UUID userId, @Param("enabled") boolean enabled, @Param("now") Instant now);

}
