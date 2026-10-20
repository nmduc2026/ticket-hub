package com.ndz.backend.shared.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bảng {@code shared.idempotency_keys}. Mỗi lệnh là một câu SQL tự commit: claim phải thấy được ngay bởi request
 * đồng thời, nên không nằm trong transaction nghiệp vụ.
 */
@Repository
class IdempotencyStore {

	// Claim nguyên tử: chèn mới, hoặc chiếm lại bản ghi đã hết hạn / bản IN_PROGRESS bị bỏ rơi.
	private static final String CLAIM = """
			INSERT INTO shared.idempotency_keys (scope, user_id, key, request_hash, status, created_at, expires_at)
			VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?)
			ON CONFLICT (scope, user_id, key) DO UPDATE SET
			    request_hash = EXCLUDED.request_hash,
			    status = 'IN_PROGRESS',
			    response_status = NULL,
			    response_body = NULL,
			    response_content_type = NULL,
			    created_at = EXCLUDED.created_at,
			    expires_at = EXCLUDED.expires_at
			WHERE idempotency_keys.expires_at <= EXCLUDED.created_at
			   OR (idempotency_keys.status = 'IN_PROGRESS' AND idempotency_keys.created_at <= ?)
			""";

	private final JdbcTemplate jdbc;

	IdempotencyStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Giành quyền xử lý một key; {@code claimedAt} là dấu nhận diện chủ sở hữu khi hoàn tất hoặc nhả.
	 *
	 * @return true nếu request này giành được quyền
	 */
	boolean claim(String scope, UUID userId, String key, String requestHash, Instant claimedAt, Instant expiresAt,
			Instant abandonedBefore) {
		return jdbc.update(CLAIM, scope, userId, key, requestHash, Timestamp.from(claimedAt), Timestamp.from(expiresAt),
				Timestamp.from(abandonedBefore)) == 1;
	}

	Optional<IdempotencyRecord> find(String scope, UUID userId, String key) {
		List<IdempotencyRecord> rows = jdbc.query("""
				SELECT request_hash, status, response_status, response_body, response_content_type
				FROM shared.idempotency_keys WHERE scope = ? AND user_id = ? AND key = ?
				""", (rs, i) -> {
			boolean completed = "COMPLETED".equals(rs.getString("status"));
			StoredResponse response = completed ? new StoredResponse(rs.getInt("response_status"),
					rs.getString("response_body"), rs.getString("response_content_type")) : null;
			return new IdempotencyRecord(rs.getString("request_hash"), completed, response);
		}, scope, userId, key);
		return rows.stream().findFirst();
	}

	/** Chỉ chủ sở hữu (đúng {@code claimedAt}) mới hoàn tất được; bản đã bị claim lại bởi request khác thì bỏ qua. */
	void complete(String scope, UUID userId, String key, Instant claimedAt, StoredResponse response) {
		jdbc.update("""
				UPDATE shared.idempotency_keys
				SET status = 'COMPLETED', response_status = ?, response_body = ?, response_content_type = ?
				WHERE scope = ? AND user_id = ? AND key = ? AND created_at = ? AND status = 'IN_PROGRESS'
				""", response.status(), response.body(), response.contentType(), scope, userId, key,
				Timestamp.from(claimedAt));
	}

	/** Bỏ claim để client retry được (lỗi 5xx chưa phải kết quả cuối). */
	void release(String scope, UUID userId, String key, Instant claimedAt) {
		jdbc.update("""
				DELETE FROM shared.idempotency_keys
				WHERE scope = ? AND user_id = ? AND key = ? AND created_at = ? AND status = 'IN_PROGRESS'
				""", scope, userId, key, Timestamp.from(claimedAt));
	}

	int deleteExpired(Instant now) {
		return jdbc.update("DELETE FROM shared.idempotency_keys WHERE expires_at <= ?", Timestamp.from(now));
	}

}
