package com.ndz.ticket.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.ticket.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	boolean existsByBookingId(UUID bookingId);

	List<Ticket> findByBookingId(UUID bookingId);

	/** Chỉ vé của chính người dùng (chống IDOR), mới phát trước. */
	List<Ticket> findByOwnerUserIdOrderByIssuedAtDescIdAsc(UUID ownerUserId, Pageable page);

}
