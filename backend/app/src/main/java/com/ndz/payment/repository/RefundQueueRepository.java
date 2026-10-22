package com.ndz.payment.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Hàng đợi hoàn tiền khi huỷ sự kiện (BR-08). */
@Repository
public class RefundQueueRepository {

	private final JdbcTemplate jdbc;

	RefundQueueRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return true nếu vừa xếp; false nếu booking đã có trong hàng đợi */
	public boolean enqueue(UUID id, QueuedRefund refund, Instant now) {
		return jdbc.update("""
				INSERT INTO payment.refund_queue (id, booking_id, user_id, contact_email, event_title, amount_minor,
				                                  currency, next_attempt_at, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (booking_id) DO NOTHING
				""", id, refund.bookingId(), refund.userId(), refund.contactEmail(), refund.eventTitle(),
				refund.amountMinor(), refund.currency(), Timestamp.from(now), Timestamp.from(now)) == 1;
	}

	/**
	 * Nhận tối đa {@code limit} dòng tới hạn và gia hạn lease: pod khác không lấy trùng, pod chết thì dòng quay lại
	 * sau {@code lease}.
	 */
	public List<QueuedRefund> claimDue(Instant now, Duration lease, int limit) {
		return jdbc.query("""
				UPDATE payment.refund_queue q SET next_attempt_at = ?, attempts = q.attempts + 1
				WHERE q.id IN (SELECT id FROM payment.refund_queue
				               WHERE status = 'PENDING' AND next_attempt_at <= ?
				               ORDER BY next_attempt_at, id LIMIT ? FOR UPDATE SKIP LOCKED)
				RETURNING q.id, q.booking_id, q.user_id, q.contact_email, q.event_title, q.amount_minor, q.currency,
				          q.attempts
				""", (rs, i) -> new QueuedRefund(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
				rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7),
				rs.getInt(8)), Timestamp.from(now.plus(lease)), Timestamp.from(now), limit);
	}

	public void markDone(UUID id) {
		jdbc.update("UPDATE payment.refund_queue SET status = 'DONE', last_error = NULL WHERE id = ?", id);
	}

	public void retryLater(UUID id, Instant nextAttemptAt, String error) {
		jdbc.update("UPDATE payment.refund_queue SET next_attempt_at = ?, last_error = ? WHERE id = ?",
				Timestamp.from(nextAttemptAt), error, id);
	}

	public void markFailed(UUID id, String error) {
		jdbc.update("UPDATE payment.refund_queue SET status = 'FAILED', last_error = ? WHERE id = ?", error, id);
	}

	/** {@code id} và {@code attempts} chỉ có sau khi nhận từ hàng đợi. */
	public record QueuedRefund(UUID id, UUID bookingId, UUID userId, String contactEmail, String eventTitle,
			long amountMinor, String currency, int attempts) {
	}

}
