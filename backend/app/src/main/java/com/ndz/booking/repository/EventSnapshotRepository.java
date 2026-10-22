package com.ndz.booking.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.response.BookingViews.EventRules;
import com.ndz.booking.entity.EventSnapshot;
import com.ndz.catalog.event.EventPublished;
import com.ndz.catalog.event.EventUpdated;

/**
 * Bản sao thông tin sự kiện của Booking (overview.md §6.2). Mọi cập nhật có điều kiện {@code last_event_at < occurredAt}
 * nên event đến sai thứ tự hoặc gửi lại không ghi đè dữ liệu mới hơn.
 */
public interface EventSnapshotRepository extends JpaRepository<EventSnapshot, UUID> {

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT INTO booking.event_snapshots
			    (event_id, organizer_id, title, description, category, banner_url, venue_id, venue_name, city_id, city_name,
			     seat_map_id, starts_at, ends_at, sale_starts_at, status, high_demand, max_tickets_per_user,
			     platform_fee_bps, doors_open_at, age_restriction, rules, last_event_at)
			VALUES (:#{#event.eventId()}, :#{#event.organizerId()}, :#{#event.title()}, :#{#event.description()},
			        :#{#event.category()}, :#{#event.bannerUrl()}, :#{#event.venueId()}, :#{#event.venueName()},
			        :#{#event.cityId()}, :#{#event.cityName()}, :#{#event.seatMapId()}, :#{#event.startsAt()},
			        :#{#event.endsAt()}, :#{#event.saleStartsAt()}, 'PUBLISHED', :#{#event.highDemand()},
			        :#{#event.maxTicketsPerUser()}, :#{#event.platformFeeBps()}, :#{#event.doorsOpenAt()},
			        :#{#event.ageRestriction()}, :#{#event.rules()}, :#{#event.occurredAt()})
			ON CONFLICT (event_id) DO UPDATE SET
			    organizer_id = EXCLUDED.organizer_id, title = EXCLUDED.title, description = EXCLUDED.description,
			    category = EXCLUDED.category, banner_url = EXCLUDED.banner_url, venue_id = EXCLUDED.venue_id,
			    venue_name = EXCLUDED.venue_name, city_id = EXCLUDED.city_id, city_name = EXCLUDED.city_name,
			    seat_map_id = EXCLUDED.seat_map_id, starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at,
			    sale_starts_at = EXCLUDED.sale_starts_at, status = 'PUBLISHED', high_demand = EXCLUDED.high_demand,
			    max_tickets_per_user = EXCLUDED.max_tickets_per_user, platform_fee_bps = EXCLUDED.platform_fee_bps,
			    doors_open_at = EXCLUDED.doors_open_at, age_restriction = EXCLUDED.age_restriction,
			    rules = EXCLUDED.rules, last_event_at = EXCLUDED.last_event_at, version = booking.event_snapshots.version + 1
			WHERE booking.event_snapshots.last_event_at < EXCLUDED.last_event_at
			""", nativeQuery = true)
	int upsert(@Param("event") EventPublished event);

	/** Tạo bản sao khi sự kiện được publish; nếu đã có (event gửi lại) thì chỉ ghi khi event mới hơn. */
	default boolean upsertPublished(EventPublished event) {
		return upsert(event) == 1;
	}

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.event_snapshots
			SET title = :#{#event.title()}, description = :#{#event.description()}, category = :#{#event.category()},
			    banner_url = :#{#event.bannerUrl()}, doors_open_at = :#{#event.doorsOpenAt()},
			    age_restriction = :#{#event.ageRestriction()}, rules = :#{#event.rules()},
			    last_event_at = :#{#event.occurredAt()}, version = version + 1
			WHERE event_id = :#{#event.eventId()} AND last_event_at < :#{#event.occurredAt()}
			""", nativeQuery = true)
	int applyDisplay(@Param("event") EventUpdated event);

	/** @return true nếu đã áp dụng; false nếu event cũ hơn dữ liệu đang có hoặc chưa có bản sao */
	default boolean updateDisplay(EventUpdated event) {
		return applyDisplay(event) == 1;
	}

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE booking.event_snapshots SET status = :status, last_event_at = :occurredAt, version = version + 1
			WHERE event_id = :eventId AND last_event_at < :occurredAt
			""", nativeQuery = true)
	int applyStatus(@Param("eventId") UUID eventId, @Param("status") String status,
			@Param("occurredAt") Instant occurredAt);

	/** @return true nếu đã áp dụng; false nếu event cũ hơn dữ liệu đang có hoặc chưa có bản sao */
	default boolean updateStatus(UUID eventId, String status, Instant occurredAt) {
		return applyStatus(eventId, status, occurredAt) == 1;
	}

	/** Quy tắc đặt vé của sự kiện (trạng thái, giới hạn vé mỗi người), đọc từ bản sao; không đọc DB của Catalog. */
	default Optional<EventRules> rules(UUID eventId) {
		return findById(eventId).map(snapshot -> new EventRules(eventId, snapshot.getStatus(),
				snapshot.getMaxTicketsPerUser(), snapshot.getSaleStartsAt(), snapshot.isHighDemand()));
	}

	default boolean exists(UUID eventId) {
		return existsById(eventId);
	}

}
