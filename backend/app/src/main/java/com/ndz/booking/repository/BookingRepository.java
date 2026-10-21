package com.ndz.booking.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.entity.Booking;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.entity.enums.LatePayment;

/**
 * Đơn đặt vé và việc giữ ghế trong tồn kho (overview.md §10, lớp 2 — Postgres là nguồn sự thật). Đọc / tạo mới qua
 * entity; mọi chuyển trạng thái đơn và ghế là câu lệnh native có điều kiện trạng thái cũ ({@code WHERE status = ...})
 * kèm {@code version = version + 1}, nên hai tiến trình đồng thời không cùng chuyển một dòng (BR-01). Các hàm khoá /
 * cập nhật ghế phải chạy trong transaction của use case; khoá theo thứ tự {@code seat_id} cố định để tránh deadlock.
 * Phần truy vấn trả về view và {@code RETURNING} nằm ở {@link BookingRepositoryCustom}.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID>, BookingRepositoryCustom {

	boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, BookingStatus status);

	default boolean hasPendingBooking(UUID userId, UUID eventId) {
		return existsByUserIdAndEventIdAndStatus(userId, eventId, BookingStatus.PENDING_PAYMENT);
	}

	@Query("""
			select count(i) from BookingItem i join Booking b on b.id = i.id.bookingId
			where b.userId = :userId and b.eventId = :eventId and b.status in :statuses
			""")
	long countItems(@Param("userId") UUID userId, @Param("eventId") UUID eventId,
			@Param("statuses") Collection<BookingStatus> statuses);

	/** Tổng số vé của user cho sự kiện: cộng mọi booking đang chờ thanh toán và đã xác nhận (BR-02). */
	default int activeTicketCount(UUID userId, UUID eventId) {
		return (int) countItems(userId, eventId, List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));
	}

	@Query("select count(i) from BookingItem i where i.id.bookingId = :bookingId")
	long countItemsOf(@Param("bookingId") UUID bookingId);

	default int itemCount(UUID bookingId) {
		return (int) countItemsOf(bookingId);
	}

	long countByStatus(BookingStatus status);

	/** Id các booking đã cho đang {@code CONFIRMED}. */
	@Query("select b.id from Booking b where b.id in :ids and b.status = :status")
	List<UUID> findIdsByStatus(@Param("ids") Collection<UUID> ids, @Param("status") BookingStatus status);

	default Set<UUID> findConfirmed(Collection<UUID> bookingIds) {
		return new HashSet<>(findIdsByStatus(bookingIds, BookingStatus.CONFIRMED));
	}

	/**
	 * Booking chờ thanh toán đã quá hạn giữ ghế: hạn là {@code expires_at}, hoặc {@code expires_at + ân hạn} nếu đã bắt
	 * đầu thanh toán (BR-03). Chỉ trả id, theo lô; use case kiểm lại dưới khoá trước khi đổi.
	 */
	@Query(value = """
			SELECT id FROM booking.bookings
			WHERE status = 'PENDING_PAYMENT'
			  AND (CASE WHEN payment_started_at IS NULL THEN expires_at
			            ELSE expires_at + :graceSeconds * interval '1 second' END) <= :now
			ORDER BY expires_at LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findDueForExpiryIds(@Param("now") Instant now, @Param("graceSeconds") long graceSeconds,
			@Param("limit") int limit);

	default List<UUID> findDueForExpiry(Instant now, Duration paymentGrace, int limit) {
		return findDueForExpiryIds(now, paymentGrace.toSeconds(), limit);
	}

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE booking.bookings SET status = :to, version = version + 1 WHERE id = :id AND status = :from",
			nativeQuery = true)
	int updateStatus(@Param("id") UUID bookingId, @Param("from") String from, @Param("to") String to);

	/** Chuyển trạng thái có điều kiện trạng thái cũ; trả false nếu booking đã bị tiến trình khác chuyển. */
	default boolean transition(UUID bookingId, BookingStatus from, BookingStatus to) {
		return updateStatus(bookingId, from.name(), to.name()) == 1;
	}

	/**
	 * Giữ ghế bằng cập nhật có điều kiện: chỉ ghế còn {@code AVAILABLE} được đổi. Người gọi so số dòng với số ghế yêu
	 * cầu và hoàn tác nếu khác (BR-01).
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.seat_inventory SET status = 'HELD', booking_id = :bookingId, version = version + 1
			WHERE event_id = :eventId AND seat_id IN (:seatIds) AND status = 'AVAILABLE'
			""", nativeQuery = true)
	int markHeld(@Param("eventId") UUID eventId, @Param("seatIds") List<UUID> seatIds,
			@Param("bookingId") UUID bookingId);

	/**
	 * Bán lại đúng các ghế cho booking đã hết hạn / bị huỷ mà thanh toán đến muộn: chỉ ghế còn {@code AVAILABLE} được
	 * đổi sang {@code SOLD}. Người gọi đã khoá ghế và so số dòng với số ghế (BR-01).
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.seat_inventory SET status = 'SOLD', booking_id = :bookingId, version = version + 1
			WHERE event_id = :eventId AND seat_id IN (:seatIds) AND status = 'AVAILABLE'
			""", nativeQuery = true)
	int sellAvailableSeats(@Param("eventId") UUID eventId, @Param("seatIds") List<UUID> seatIds,
			@Param("bookingId") UUID bookingId);

	/** Ghế đang giữ của booking thành đã bán; trả về số ghế đã đổi (phải bằng số ghế của booking, BR-01). */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.seat_inventory SET status = 'SOLD', version = version + 1
			WHERE booking_id = :bookingId AND status = 'HELD'
			""", nativeQuery = true)
	int markSold(@Param("bookingId") UUID bookingId);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.bookings SET late_payment = :latePayment, version = version + 1
			WHERE id = :bookingId AND status IN ('EXPIRED', 'CANCELLED')
			  AND (:latePayment = 'REFUND_PENDING' OR late_payment IS NOT NULL)
			""", nativeQuery = true)
	int updateLatePayment(@Param("bookingId") UUID bookingId, @Param("latePayment") String latePayment);

	/**
	 * Ghi trạng thái tiền của thanh toán đến muộn (§7.3), chỉ cho booking {@code EXPIRED} / {@code CANCELLED}. Kết quả
	 * hoàn chỉ ghi khi booking đã được đánh dấu có thanh toán đến muộn.
	 *
	 * @return true nếu đã ghi
	 */
	default boolean markLatePayment(UUID bookingId, LatePayment latePayment) {
		return updateLatePayment(bookingId, latePayment.name()) == 1;
	}

	/** Ghi mốc bắt đầu thanh toán một lần (BR-03: từ đó booking được ân hạn thêm); gọi lại không đổi mốc. */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.bookings SET payment_started_at = :now, version = version + 1
			WHERE id = :bookingId AND payment_started_at IS NULL
			""", nativeQuery = true)
	int markPaymentStarted(@Param("bookingId") UUID bookingId, @Param("now") Instant now);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.bookings
			SET status = 'REFUND_REQUESTED', refund_reason = :reason, refund_amount_minor = :amount,
			    refund_requested_at = :now, version = version + 1
			WHERE id = :bookingId AND status = 'CONFIRMED'
			""", nativeQuery = true)
	int updateRefundRequested(@Param("bookingId") UUID bookingId, @Param("reason") String reason,
			@Param("amount") long refundAmountMinor, @Param("now") Instant now);

	/**
	 * Ghi yêu cầu hoàn: {@code CONFIRMED → REFUND_REQUESTED} kèm lý do và số tiền hoàn đã chốt. Điều kiện trạng thái cũ
	 * nằm trong câu lệnh nên hai yêu cầu đồng thời chỉ một cái qua.
	 */
	default boolean markRefundRequested(UUID bookingId, String reason, long refundAmountMinor, Instant now) {
		return updateRefundRequested(bookingId, reason, refundAmountMinor, now) == 1;
	}

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.bookings
			SET status = 'REFUND_REQUESTED', refund_requested_at = :now, refund_failed_at = NULL, version = version + 1
			WHERE id = :bookingId AND status = 'REFUND_FAILED'
			""", nativeQuery = true)
	int updateRefundRetried(@Param("bookingId") UUID bookingId, @Param("now") Instant now);

	/** Admin hoàn thủ công: {@code REFUND_FAILED → REFUND_REQUESTED} cho lần thử mới. */
	default boolean markRefundRetried(UUID bookingId, Instant now) {
		return updateRefundRetried(bookingId, now) == 1;
	}

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE booking.bookings SET refund_failed_at = :at WHERE id = :bookingId", nativeQuery = true)
	int markRefundFailedAt(@Param("bookingId") UUID bookingId, @Param("at") Instant at);

}
