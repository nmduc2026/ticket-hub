package com.ndz.backend.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.backend.catalog.entity.TicketTier;

public interface TicketTierRepository extends JpaRepository<TicketTier, UUID> {

	/** UUIDv7 sắp theo thời gian tạo nên thứ tự id là thứ tự tạo hạng vé. */
	List<TicketTier> findByEventIdOrderById(UUID eventId);

	Optional<TicketTier> findByIdAndEventId(UUID id, UUID eventId);

}
