package com.ndz.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.catalog.entity.Venue;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

	boolean existsByWardId(UUID wardId);

	Optional<Venue> findByCode(String code);

}
