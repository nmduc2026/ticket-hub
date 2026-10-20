package com.ndz.backend.account.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ndz.backend.shared.CurrentUser;

/** Upsert hồ sơ trong một câu lệnh: tạo nếu chưa có, chỉ ghi khi claim khác bản ghi (ADR 0016). */
@Repository
public class UserProfileWriter {

	// `enabled` không nằm trong SET: do Admin khoá / mở khoá (Keycloak là nguồn sự thật cho đăng nhập).
	private static final String UPSERT = """
			INSERT INTO account.user_profiles
			    (user_id, email, full_name, username, roles, enabled, last_login_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?)
			ON CONFLICT (user_id) DO UPDATE SET
			    email = EXCLUDED.email,
			    full_name = EXCLUDED.full_name,
			    username = EXCLUDED.username,
			    roles = EXCLUDED.roles,
			    last_login_at = GREATEST(user_profiles.last_login_at, EXCLUDED.last_login_at),
			    updated_at = EXCLUDED.updated_at
			WHERE (user_profiles.email, user_profiles.full_name, user_profiles.username, user_profiles.roles)
			        IS DISTINCT FROM (EXCLUDED.email, EXCLUDED.full_name, EXCLUDED.username, EXCLUDED.roles)
			   OR user_profiles.last_login_at IS DISTINCT FROM GREATEST(user_profiles.last_login_at, EXCLUDED.last_login_at)
			""";

	private final JdbcTemplate jdbc;

	UserProfileWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void upsert(CurrentUser user, Instant lastLoginAt, Instant now) {
		Set<String> sortedRoles = new TreeSet<>(user.roles());
		jdbc.update(connection -> {
			var statement = connection.prepareStatement(UPSERT);
			statement.setObject(1, user.id());
			statement.setString(2, user.email());
			statement.setString(3, user.fullName());
			statement.setString(4, user.username());
			statement.setArray(5, connection.createArrayOf("text", sortedRoles.toArray()));
			statement.setTimestamp(6, Timestamp.from(lastLoginAt));
			statement.setTimestamp(7, Timestamp.from(now));
			statement.setTimestamp(8, Timestamp.from(now));
			return statement;
		});
	}

}
