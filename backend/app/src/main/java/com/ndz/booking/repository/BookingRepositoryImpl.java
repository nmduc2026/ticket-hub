package com.ndz.booking.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.ndz.booking.dto.response.BookingResponses.RefundProgress;
import com.ndz.booking.dto.response.BookingViews.AdminBookingSummary;
import com.ndz.booking.dto.response.BookingViews.AffectedBooking;
import com.ndz.booking.dto.response.BookingViews.BookingItemView;
import com.ndz.booking.dto.response.BookingViews.BookingSummary;
import com.ndz.booking.dto.response.BookingViews.BookingView;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.HoldExpiringBooking;
import com.ndz.booking.dto.response.BookingViews.HeldSeat;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.LockedSeat;
import com.ndz.booking.dto.response.BookingViews.PaymentInfo;
import com.ndz.booking.dto.response.BookingViews.RefundContext;
import com.ndz.booking.entity.Booking;
import com.ndz.booking.entity.BookingItem;
import com.ndz.booking.entity.SeatInventory;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.entity.enums.LatePayment;
import com.ndz.shared.util.PlatformFee;

/**
 * Các truy vấn của {@link BookingRepository} không viết được bằng method Spring Data. Khoá dòng dùng native
 * {@code FOR UPDATE} (Hibernate sinh {@code FOR NO KEY UPDATE} cho PESSIMISTIC_WRITE, khác với khoá ghế ở overview.md
 * §10) và tách entity khỏi persistence context ngay sau khi đọc, để câu lệnh cập nhật native phía sau không để lại
 * bản sao cũ.
 */
public class BookingRepositoryImpl implements BookingRepositoryCustom {

	@PersistenceContext
	private EntityManager em;

	@Override
	public List<LockedSeat> lockSeats(UUID eventId, List<UUID> seatIds) {
		List<SeatInventory> rows = em.createNativeQuery("""
				SELECT * FROM booking.seat_inventory
				WHERE event_id = :eventId AND seat_id IN (:seatIds)
				ORDER BY seat_id
				FOR UPDATE
				""", SeatInventory.class).setParameter("eventId", eventId).setParameter("seatIds", seatIds)
				.getResultList();
		rows.forEach(em::detach);
		return rows.stream().map(seat -> new LockedSeat(seat.getSeatId(), seat.getTierId(), seat.getTierName(),
				seat.getSectionCode(), seat.getRowLabel(), seat.getSeatNumber(), seat.getPriceMinor(),
				seat.getCurrency(), seat.getStatus())).toList();
	}

	@Override
	public Optional<LockedBooking> lockBooking(UUID bookingId) {
		List<Booking> rows = em.createNativeQuery("SELECT * FROM booking.bookings WHERE id = :id FOR UPDATE",
				Booking.class).setParameter("id", bookingId).getResultList();
		rows.forEach(em::detach);
		return rows.stream().findFirst().map(booking -> new LockedBooking(booking.getId(), booking.getUserId(),
				booking.getEventId(), booking.getStatus(), booking.getExpiresAt(), booking.getPaymentStartedAt()));
	}

	@Override
	public List<HeldSeat> findHeldSeats() {
		List<Object[]> rows = em.createQuery("""
				select s.id.eventId, s.id.seatId, b.id, b.expiresAt, b.paymentStartedAt
				from SeatInventory s join Booking b on b.id = s.bookingId
				where s.status = 'HELD' and b.status = :pending
				""", Object[].class).setParameter("pending", BookingStatus.PENDING_PAYMENT).getResultList();
		return rows.stream().map(row -> new HeldSeat((UUID) row[0], (UUID) row[1], (UUID) row[2], (Instant) row[3],
				(Instant) row[4])).toList();
	}

	@Override
	public List<UUID> seatIdsOf(UUID bookingId) {
		return em.createQuery("select i.id.seatId from BookingItem i where i.id.bookingId = :bookingId", UUID.class)
				.setParameter("bookingId", bookingId).getResultList();
	}

	@Override
	public void insertItems(UUID bookingId, UUID eventId, List<LockedSeat> seats) {
		for (LockedSeat seat : seats) {
			em.persist(new BookingItem(bookingId, eventId, seat.seatId(), seat.tierId(), seat.priceMinor()));
		}
	}

	@Override
	public Optional<BookingView> findOwned(UUID bookingId, UUID userId) {
		List<Object[]> rows = em.createQuery("""
				select b.id, b.eventId, b.status, b.totalMinor, b.currency, b.expiresAt, b.latePayment
				from Booking b where b.id = :id and b.userId = :userId
				""", Object[].class).setParameter("id", bookingId).setParameter("userId", userId).getResultList();
		return rows.stream().findFirst().map(row -> new BookingView((UUID) row[0], (UUID) row[1],
				((BookingStatus) row[2]).name(), (Long) row[3], (String) row[4], (Instant) row[5],
				row[6] == null ? null : ((LatePayment) row[6]).name(), items(bookingId)));
	}

	@Override
	public List<BookingSummary> findByUser(UUID userId, int limit, int offset, Duration refundCutoff) {
		List<Object[]> rows = em.createQuery("""
				select b.id, e.title, e.startsAt, b.status, b.totalMinor, b.currency, b.createdAt, b.refundAmountMinor,
				       e.platformFeeBps, (select count(i) from BookingItem i where i.id.bookingId = b.id)
				from Booking b join EventSnapshot e on e.eventId = b.eventId
				where b.userId = :userId
				order by b.createdAt desc, b.id
				""", Object[].class).setParameter("userId", userId).setFirstResult(offset).setMaxResults(limit)
				.getResultList();
		return rows.stream().map(row -> {
			BookingStatus status = (BookingStatus) row[3];
			long total = (Long) row[4];
			int feeBps = (Integer) row[8];
			Instant startsAt = (Instant) row[2];
			boolean confirmed = status == BookingStatus.CONFIRMED;
			return new BookingSummary((UUID) row[0], (String) row[1], startsAt, status.name(), total, (String) row[5],
					(Instant) row[6], refundStatus(status), (Long) row[7], ((Long) row[9]).intValue(),
					PlatformFee.of(total, feeBps), confirmed ? total - PlatformFee.of(total, feeBps) : null,
					confirmed ? startsAt.minus(refundCutoff) : null);
		}).toList();
	}

	/** {@code REQUESTED}, {@code REFUNDED}, {@code FAILED} khi đơn đã qua bước yêu cầu hoàn, ngược lại null. */
	private static String refundStatus(BookingStatus status) {
		return switch (status) {
			case REFUND_REQUESTED -> "REQUESTED";
			case REFUNDED -> "REFUNDED";
			case REFUND_FAILED -> "FAILED";
			default -> null;
		};
	}

	@Override
	public List<AdminBookingSummary> findByStatus(BookingStatus status, int limit, int offset) {
		List<Object[]> rows = em.createQuery("""
				select b.id, b.contactEmail, e.title, b.totalMinor, b.currency, b.refundFailedAt
				from Booking b join EventSnapshot e on e.eventId = b.eventId
				where b.status = :status
				order by b.refundFailedAt asc nulls last, b.createdAt, b.id
				""", Object[].class).setParameter("status", status).setFirstResult(offset).setMaxResults(limit)
				.getResultList();
		return rows.stream().map(row -> new AdminBookingSummary((UUID) row[0], (String) row[1], (String) row[2],
				(Long) row[3], (String) row[4], (Instant) row[5])).toList();
	}

	@Override
	public RefundContext refundContext(UUID bookingId) {
		Object[] row = em.createQuery("select b.refundReason, b.refundAmountMinor from Booking b where b.id = :id",
				Object[].class).setParameter("id", bookingId).getSingleResult();
		return new RefundContext((String) row[0], (Long) row[1]);
	}

	@Override
	public PaymentInfo paymentInfo(UUID bookingId) {
		Object[] row = em.createQuery("""
				select b.totalMinor, b.currency, e.platformFeeBps, e.organizerId
				from Booking b join EventSnapshot e on e.eventId = b.eventId where b.id = :id
				""", Object[].class).setParameter("id", bookingId).getSingleResult();
		return new PaymentInfo((Long) row[0], (String) row[1], (Integer) row[2], (UUID) row[3]);
	}

	@Override
	public ConfirmationData confirmationData(UUID bookingId) {
		Object[] row = em.createQuery("""
				select b.id, b.userId, b.eventId, e.title, e.startsAt, e.venueName, b.contactEmail, b.totalMinor,
				       b.currency, e.organizerId
				from Booking b join EventSnapshot e on e.eventId = b.eventId where b.id = :id
				""", Object[].class).setParameter("id", bookingId).getSingleResult();
		return new ConfirmationData((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3], (Instant) row[4],
				(String) row[5], (String) row[6], (Long) row[7], (String) row[8], (UUID) row[9], items(bookingId));
	}

	@Override
	public List<UUID> releaseSeats(UUID bookingId) {
		return releaseWhere(bookingId, "HELD");
	}

	@Override
	public List<UUID> releaseSoldSeats(UUID bookingId) {
		return releaseWhere(bookingId, "SOLD");
	}

	@Override
	public RefundProgress refundProgress(UUID eventId, String reason) {
		Object[] row = (Object[]) em.createNativeQuery("""
				SELECT count(*), count(*) FILTER (WHERE status = 'REFUNDED'),
				       count(*) FILTER (WHERE status = 'REFUND_REQUESTED'), count(*) FILTER (WHERE status = 'REFUND_FAILED')
				FROM booking.bookings WHERE event_id = :eventId AND refund_reason = :reason
				""").setParameter("eventId", eventId).setParameter("reason", reason).getSingleResult();
		return new RefundProgress(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
				((Number) row[2]).intValue(), ((Number) row[3]).intValue());
	}

	@Override
	public List<AffectedBooking> affectedBySalePause(UUID eventId) {
		return em.createQuery("""
				select b.id, b.userId, b.contactEmail, b.status from Booking b
				where b.eventId = :eventId and b.status in :statuses order by b.id
				""", Object[].class).setParameter("eventId", eventId)
				.setParameter("statuses", List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED)).getResultList()
				.stream().map(row -> new AffectedBooking((UUID) row[0], (UUID) row[1], (String) row[2],
						((BookingStatus) row[3]).name()))
				.toList();
	}

	@Override
	public List<HoldExpiringBooking> markHoldExpiringNotified(Instant now, Instant threshold, int limit) {
		em.flush();
		// Đánh dấu và đọc cùng một câu lệnh: hai lần chạy chồng nhau không báo trùng một booking.
		List<?> rows = em.createNativeQuery("""
				UPDATE booking.bookings b SET hold_expiring_notified_at = :now, version = b.version + 1
				FROM booking.event_snapshots e
				WHERE e.event_id = b.event_id
				  AND b.id IN (SELECT id FROM booking.bookings
				               WHERE status = 'PENDING_PAYMENT' AND payment_started_at IS NULL
				                 AND hold_expiring_notified_at IS NULL AND expires_at > :now AND expires_at <= :threshold
				               ORDER BY expires_at LIMIT :limit FOR UPDATE SKIP LOCKED)
				RETURNING b.id, b.user_id, b.event_id, e.title, b.contact_email, b.expires_at
				""").setParameter("now", now).setParameter("threshold", threshold).setParameter("limit", limit)
				.getResultList();
		em.clear();
		return rows.stream().map(Object[].class::cast)
				.map(row -> new HoldExpiringBooking((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3],
						(String) row[4], toInstant(row[5])))
				.toList();
	}

	private static Instant toInstant(Object value) {
		return value instanceof java.time.OffsetDateTime offset ? offset.toInstant() : (Instant) value;
	}

	private List<UUID> releaseWhere(UUID bookingId, String fromStatus) {
		em.flush();
		List<?> released = em.createNativeQuery("""
				UPDATE booking.seat_inventory SET status = 'AVAILABLE', booking_id = NULL, version = version + 1
				WHERE booking_id = :bookingId AND status = :fromStatus
				RETURNING seat_id
				""").setParameter("bookingId", bookingId).setParameter("fromStatus", fromStatus).getResultList();
		em.clear();
		return released.stream().map(UUID.class::cast).toList();
	}

	@Override
	public void recordRevokedTicket(UUID bookingId, UUID ticketId, Instant now) {
		em.flush();
		em.createNativeQuery("""
				INSERT INTO booking.revoked_tickets (booking_id, ticket_id, revoked_at)
				VALUES (:bookingId, :ticketId, :now)
				ON CONFLICT (booking_id, ticket_id) DO NOTHING
				""").setParameter("bookingId", bookingId).setParameter("ticketId", ticketId).setParameter("now", now)
				.executeUpdate();
	}

	@Override
	public int revokedTicketCount(UUID bookingId) {
		return em.createQuery("select count(r) from RevokedTicket r where r.id.bookingId = :bookingId", Long.class)
				.setParameter("bookingId", bookingId).getSingleResult().intValue();
	}

	private List<BookingItemView> items(UUID bookingId) {
		List<Object[]> rows = em.createQuery("""
				select i.id.seatId, s.sectionCode, s.rowLabel, s.seatNumber, i.tierId, s.tierName, i.priceMinor
				from BookingItem i join SeatInventory s on s.id.eventId = i.eventId and s.id.seatId = i.id.seatId
				where i.id.bookingId = :bookingId
				order by s.sectionCode, s.rowLabel, s.seatNumber
				""", Object[].class).setParameter("bookingId", bookingId).getResultList();
		return rows.stream().map(row -> new BookingItemView((UUID) row[0], (String) row[1], (String) row[2],
				(Integer) row[3], (UUID) row[4], (String) row[5], (Long) row[6])).toList();
	}

}
