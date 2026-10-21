package com.ndz.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.entity.enums.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	/**
	 * Check-in nguyên tử (BR-09, §7.5): chỉ vé còn {@code VALID} mới thành {@code USED}. Trả 0 nghĩa là vé đã dùng, đã
	 * thu hồi hoặc không tồn tại; người gọi đọc lại trạng thái để báo đúng lý do.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE ticket.tickets SET status = 'USED', version = version + 1 "
			+ "WHERE id = :id AND status = 'VALID'", nativeQuery = true)
	int markUsed(@Param("id") UUID id);

	boolean existsByBookingId(UUID bookingId);

	List<Ticket> findByBookingId(UUID bookingId);

	/** Ghế đã có vé chưa thu hồi: phát thêm vé cho ghế này sẽ vi phạm {@code uq_ticket_seat_active} (BR-01). */
	boolean existsByEventIdAndSeatIdAndStatusNot(UUID eventId, UUID seatId, TicketStatus status);

	/** Chỉ vé của chính người dùng (chống IDOR), mới phát trước. */
	List<Ticket> findByOwnerUserIdOrderByIssuedAtDescIdAsc(UUID ownerUserId, Pageable page);

	boolean existsByOwnerUserIdAndStatusAndStartsAtAfter(UUID ownerUserId, TicketStatus status, Instant startsAt);

}
