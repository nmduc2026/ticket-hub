package com.ndz.ticket.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Khoá công khai dùng để xác minh QR theo {@code key_id} (overview.md §6.4, §7.5). */
@Repository
public class SigningKeyRepository {

	private final JdbcTemplate jdbc;

	SigningKeyRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Đặt khoá này làm khoá ACTIVE duy nhất: khoá ACTIVE khác chuyển RETIRED (giữ lại để vé đã phát vẫn xác minh
	 * được), khoá chưa có thì thêm. Gọi lại với cùng khoá không đổi gì.
	 */
	@Transactional
	public void activate(String keyId, String publicJwk) {
		jdbc.update("UPDATE ticket.signing_keys SET status = 'RETIRED' WHERE status = 'ACTIVE' AND key_id <> ?", keyId);
		jdbc.update("""
				INSERT INTO ticket.signing_keys (key_id, public_key, status) VALUES (?, ?, 'ACTIVE')
				ON CONFLICT (key_id) DO UPDATE SET status = 'ACTIVE'
				""", keyId, publicJwk);
	}

	/** JWK công khai (JSON) của khoá; rỗng nếu {@code keyId} chưa từng có. */
	public Optional<String> findPublicKey(String keyId) {
		return jdbc.queryForList("SELECT public_key FROM ticket.signing_keys WHERE key_id = ?", String.class, keyId)
				.stream().findFirst();
	}

}
