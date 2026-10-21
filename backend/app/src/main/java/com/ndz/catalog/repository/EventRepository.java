package com.ndz.catalog.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.enums.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

	/** Chống IDOR: chỉ tìm trong sự kiện của chính organizer. */
	Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

	List<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

	/** Sự kiện đã publish, tới giờ mở bán và có tồn kho: ứng viên chuyển sang ON_SALE (theo lô). */
	List<Event> findTop100ByStatusAndInventoryReadyTrueAndSaleStartsAtLessThanEqualOrderBySaleStartsAt(
			EventStatus status, Instant now);

	/** Sự kiện chưa kết thúc nhưng đã qua giờ kết thúc: ứng viên chuyển sang ENDED (theo lô). */
	List<Event> findTop100ByStatusInAndEndsAtLessThanEqualOrderByEndsAt(Collection<EventStatus> statuses,
			Instant now);

}
