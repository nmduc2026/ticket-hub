package com.ndz.payment.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Connected account Stripe Express của organizer (overview.md §6.3); cập nhật từ webhook {@code account.updated}. */
@Repository
public class ConnectedAccountRepository {

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;

	ConnectedAccountRepository(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public Optional<ConnectedAccount> findByOrganizer(UUID organizerId) {
		return jdbc.query("""
				SELECT organizer_id, stripe_account_id, charges_enabled, payouts_enabled, details_submitted,
				       requirements_due::text, disabled_reason
				FROM payment.connected_accounts WHERE organizer_id = ?
				""", (rs, i) -> new ConnectedAccount(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3),
				rs.getBoolean(4), rs.getBoolean(5), json.readValue(rs.getString(6), STRINGS), rs.getString(7)),
				organizerId).stream().findFirst();
	}

	/**
	 * Số connected account ở state {@code restricted} (overview.md §7.6: chưa {@code charges_enabled}, đã nộp đủ hồ sơ,
	 * không còn requirement chờ xử lý, và {@code disabled_reason} không phải đang chờ xác minh). Admin dashboard.
	 */
	public int countRestricted() {
		return jdbc.queryForObject("""
				SELECT count(*) FROM payment.connected_accounts
				WHERE charges_enabled = false AND details_submitted = true AND requirements_due = '[]'::jsonb
				  AND disabled_reason IS NOT NULL AND disabled_reason <> 'requirements.pending_verification'
				""", Integer.class);
	}

	/**
	 * Số connected account theo {@code state} (trừ {@code none}, vì đó là organizer chưa có dòng nào) — cùng thứ tự
	 * suy luận với {@link com.ndz.payment.service.impl.OrganizerStripeServiceImpl#toResponse}, cho Admin xem cấu hình
	 * thanh toán (màn #36).
	 */
	public Map<String, Integer> countByState() {
		return jdbc.query("""
				SELECT
				    CASE
				        WHEN charges_enabled THEN 'active'
				        WHEN NOT details_submitted OR requirements_due <> '[]'::jsonb THEN 'incomplete'
				        WHEN disabled_reason IS NULL OR disabled_reason = 'requirements.pending_verification' THEN 'pending'
				        ELSE 'restricted'
				    END AS state,
				    count(*) AS total
				FROM payment.connected_accounts
				GROUP BY 1
				""", rs -> {
			Map<String, Integer> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getString("state"), rs.getInt("total"));
			}
			return result;
		});
	}

	/** Tổng số connected account đang có (dùng để suy ra số organizer ở state {@code none}). */
	public int countTotal() {
		Integer total = jdbc.queryForObject("SELECT count(*) FROM payment.connected_accounts", Integer.class);
		return total == null ? 0 : total;
	}

	/** Lưu account vừa tạo; hai request đồng thời cùng tạo (cùng idempotency key) chỉ một dòng được ghi. */
	public void insertIfAbsent(UUID organizerId, String stripeAccountId, Instant now) {
		jdbc.update("""
				INSERT INTO payment.connected_accounts (organizer_id, stripe_account_id, created_at, updated_at)
				VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", organizerId, stripeAccountId, Timestamp.from(now), Timestamp.from(now));
	}

	/**
	 * Ghi trạng thái từ {@code account.updated}.
	 *
	 * @return organizer và giá trị {@code charges_enabled} trước khi ghi; rỗng nếu account không thuộc organizer nào
	 */
	public Optional<Change> applyUpdate(String stripeAccountId, boolean chargesEnabled, boolean payoutsEnabled,
			boolean detailsSubmitted, List<String> requirementsDue, String disabledReason, Instant now) {
		return jdbc.query("""
				WITH old AS (SELECT organizer_id, charges_enabled, details_submitted, requirements_due, disabled_reason
				             FROM payment.connected_accounts WHERE stripe_account_id = ? FOR UPDATE)
				UPDATE payment.connected_accounts c
				SET charges_enabled = ?, payouts_enabled = ?, details_submitted = ?, requirements_due = ?::jsonb,
				    disabled_reason = ?, updated_at = ?, version = c.version + 1
				FROM old WHERE c.organizer_id = old.organizer_id
				RETURNING c.organizer_id, old.charges_enabled,
				          (NOT old.charges_enabled AND old.details_submitted AND old.requirements_due = '[]'::jsonb
				           AND old.disabled_reason IS NOT NULL
				           AND old.disabled_reason <> 'requirements.pending_verification')
				""", (rs, i) -> new Change(rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getBoolean(3)), stripeAccountId,
				chargesEnabled, payoutsEnabled, detailsSubmitted, json.writeValueAsString(requirementsDue),
				disabledReason, Timestamp.from(now)).stream().findFirst();
	}

	public record ConnectedAccount(UUID organizerId, String stripeAccountId, boolean chargesEnabled,
			boolean payoutsEnabled, boolean detailsSubmitted, List<String> requirementsDue, String disabledReason) {
	}

	/** {@code restrictedBefore}: trước khi ghi, account đã ở state {@code restricted} (cùng điều kiện {@link #countRestricted}). */
	public record Change(UUID organizerId, boolean chargesEnabledBefore, boolean restrictedBefore) {
	}

}
