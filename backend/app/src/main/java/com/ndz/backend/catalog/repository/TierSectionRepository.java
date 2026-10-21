package com.ndz.backend.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.backend.catalog.entity.TierSection;

public interface TierSectionRepository extends JpaRepository<TierSection, TierSection.Key> {

	@Query("select t from TierSection t where t.id.eventId = :eventId")
	List<TierSection> findByEventId(@Param("eventId") UUID eventId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TierSection t where t.id.eventId = :eventId")
	void deleteByEventId(@Param("eventId") UUID eventId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TierSection t where t.tierId = :tierId")
	void deleteByTierId(@Param("tierId") UUID tierId);

}
