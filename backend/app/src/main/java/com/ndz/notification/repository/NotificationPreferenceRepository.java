package com.ndz.notification.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cài đặt nhận thông báo theo loại (overview.md §6.5). Không có dòng nghĩa là mặc định bật cả hai kênh. */
@Repository
public class NotificationPreferenceRepository {

	/** Lựa chọn của người dùng cho một loại. */
	public record Preference(boolean email, boolean inApp) {

		public static final Preference DEFAULT = new Preference(true, true);

	}

	private final JdbcTemplate jdbc;

	NotificationPreferenceRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Các lựa chọn đã lưu của người dùng, theo mã loại. */
	public Map<String, Preference> findAll(UUID userId) {
		Map<String, Preference> result = new HashMap<>();
		jdbc.query("SELECT type, email, in_app FROM notification.notification_preferences WHERE user_id = ?",
				rs -> {
					result.put(rs.getString("type"), new Preference(rs.getBoolean("email"), rs.getBoolean("in_app")));
				}, userId);
		return result;
	}

	public void upsert(UUID userId, String type, boolean email, boolean inApp) {
		jdbc.update("""
				INSERT INTO notification.notification_preferences (user_id, type, email, in_app) VALUES (?, ?, ?, ?)
				ON CONFLICT (user_id, type) DO UPDATE SET email = EXCLUDED.email, in_app = EXCLUDED.in_app
				""", userId, type, email, inApp);
	}

}
