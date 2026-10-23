package com.ndz.booking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.booking.entity.SeatInventory;
import com.ndz.booking.entity.SeatInventory.SeatInventoryId;

/** Tồn kho ghế theo sự kiện (overview.md §6.2). */
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, SeatInventoryId>,
		SeatInventoryRepositoryCustom {

	@Query("select count(s) from SeatInventory s where s.id.eventId = :eventId")
	long countByEvent(@Param("eventId") UUID eventId);

	@Query("select s.id.seatId from SeatInventory s where s.id.eventId = :eventId")
	List<UUID> findSeatIdsByEvent(@Param("eventId") UUID eventId);

	default int count(UUID eventId) {
		return (int) countByEvent(eventId);
	}

}
