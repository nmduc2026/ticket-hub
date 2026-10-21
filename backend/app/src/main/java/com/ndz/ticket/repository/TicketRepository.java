package com.ndz.ticket.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.ticket.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	boolean existsByBookingId(UUID bookingId);

	List<Ticket> findByBookingId(UUID bookingId);

	/** Ghế đã có vé chưa thu hồi: phát thêm vé cho ghế này sẽ vi phạm {@code uq_ticket_seat_active} (BR-01). */
	boolean existsByEventIdAndSeatIdAndStatusNot(UUID eventId, UUID seatId, com.ndz.ticket.entity.enums.TicketStatus status);

	/** Chỉ vé của chính người dùng (chống IDOR), mới phát trước. */
	List<Ticket> findByOwnerUserIdOrderByIssuedAtDescIdAsc(UUID ownerUserId, Pageable page);

}
