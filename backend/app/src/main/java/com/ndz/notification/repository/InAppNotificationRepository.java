package com.ndz.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hộp thư thông báo trong app (overview.md §6.5). Mọi truy vấn đọc / sửa đều lọc theo {@code user_id} của người đang
 * đăng nhập (chống IDOR): thông báo của người khác coi như không tồn tại.
 */
@Repository
public class InAppNotificationRepository {

	/** Một dòng hộp thư. */
	public record Row(UUID id, String type, String title, String body, String link, Instant createdAt,
			Instant readAt) {
	}

	private final JdbcTemplate jdbc;

	InAppNotificationRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Ghi thông báo; cùng {@code (user_id, source_event_id)} đã có thì bỏ qua.
	 *
	 * @return true nếu là thông báo mới
	 */
	public boolean insertIfAbsent(UUID id, UUID userId, String type, String title, String body, String link,
			UUID sourceEventId, Instant createdAt) {
		return jdbc.update("""
				INSERT INTO notification.notifications (id, user_id, type, title, body, link, source_event_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id, source_event_id) DO NOTHING
				""", id, userId, type, title, body, link, sourceEventId, Timestamp.from(createdAt)) == 1;
	}

	/** Mới nhất trước; {@code beforeId} là con trỏ (id của bản cuối trang trước), null cho trang đầu. */
	public List<Row> list(UUID userId, boolean unreadOnly, UUID beforeId, int limit) {
		return jdbc.query("""
				SELECT id, type, title, body, link, created_at, read_at FROM notification.notifications
				WHERE user_id = ? AND (? = FALSE OR read_at IS NULL)
				  AND (?::uuid IS NULL OR (created_at, id) < (SELECT created_at, id FROM notification.notifications
				                                              WHERE id = ?::uuid AND user_id = ?))
				ORDER BY created_at DESC, id DESC LIMIT ?
				""", (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"),
				rs.getString("body"), rs.getString("link"), rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()), userId,
				unreadOnly, beforeId, beforeId, userId, limit);
	}

	public int unreadCount(UUID userId) {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM notification.notifications WHERE user_id = ? AND read_at IS NULL", Integer.class,
				userId);
		return count == null ? 0 : count;
	}

	/** Đánh dấu đã đọc; đã đọc rồi vẫn tính là thành công. @return false nếu không phải thông báo của người này */
	public boolean markRead(UUID userId, UUID id, Instant now) {
		jdbc.update("UPDATE notification.notifications SET read_at = ? WHERE id = ? AND user_id = ? AND read_at IS NULL",
				Timestamp.from(now), id, userId);
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM notification.notifications WHERE id = ? AND user_id = ?)", Boolean.class,
				id, userId));
	}

	/** @return số thông báo vừa được đánh dấu đã đọc */
	public int markAllRead(UUID userId, Instant now) {
		return jdbc.update("UPDATE notification.notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
				Timestamp.from(now), userId);
	}

	/** Xoá thông báo tạo trước {@code cutoff}; @return số bản đã xoá */
	public int deleteCreatedBefore(Instant cutoff) {
		return jdbc.update("DELETE FROM notification.notifications WHERE created_at < ?", Timestamp.from(cutoff));
	}

}
