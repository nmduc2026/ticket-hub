package com.ndz.payment.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
				WITH old AS (SELECT organizer_id, charges_enabled FROM payment.connected_accounts
				             WHERE stripe_account_id = ? FOR UPDATE)
				UPDATE payment.connected_accounts c
				SET charges_enabled = ?, payouts_enabled = ?, details_submitted = ?, requirements_due = ?::jsonb,
				    disabled_reason = ?, updated_at = ?, version = c.version + 1
				FROM old WHERE c.organizer_id = old.organizer_id
				RETURNING c.organizer_id, old.charges_enabled
				""", (rs, i) -> new Change(rs.getObject(1, UUID.class), rs.getBoolean(2)), stripeAccountId,
				chargesEnabled, payoutsEnabled, detailsSubmitted, json.writeValueAsString(requirementsDue),
				disabledReason, Timestamp.from(now)).stream().findFirst();
	}

	public record ConnectedAccount(UUID organizerId, String stripeAccountId, boolean chargesEnabled,
			boolean payoutsEnabled, boolean detailsSubmitted, List<String> requirementsDue, String disabledReason) {
	}

	public record Change(UUID organizerId, boolean chargesEnabledBefore) {
	}

}
