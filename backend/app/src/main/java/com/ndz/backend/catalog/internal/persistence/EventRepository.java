package com.ndz.backend.catalog.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ndz.backend.catalog.internal.domain.Event;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

	/** Chống IDOR: chỉ tìm trong sự kiện của chính organizer. */
	Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

}
