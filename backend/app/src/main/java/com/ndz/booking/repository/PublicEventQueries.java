package com.ndz.booking.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.response.EventViews.EventDetail;
import com.ndz.booking.dto.response.EventViews.EventListItem;
import com.ndz.booking.dto.response.EventViews.EventPage;
import com.ndz.booking.dto.response.EventViews.SearchCriteria;
import com.ndz.booking.dto.response.EventViews.SeatState;
import com.ndz.booking.dto.response.EventViews.TierAvailability;

/**
 * Truy vấn đọc cho API công khai về sự kiện (overview.md §11.2). Dùng {@code EventSnapshot} và {@code SeatInventory}
 * của chính Booking, không đọc DB của Catalog. Mọi giá trị người dùng đi qua tham số; chuỗi tìm kiếm được escape.
 */
@Repository
@Transactional(readOnly = true)
public class PublicEventQueries {

	/** Sự kiện hiển thị ở danh sách: đang chờ mở bán, đang bán hoặc tạm dừng. Sự kiện đã kết thúc / huỷ ẩn khỏi danh sách. */
	private static final String LISTED = "e.status in ('PUBLISHED', 'ON_SALE', 'SUSPENDED') and e.endsAt > :now";

	private static final char ESCAPE = '!';

	@PersistenceContext
	private EntityManager em;

	public EventPage search(SearchCriteria criteria, int page, int size, Instant now) {
		StringBuilder where = new StringBuilder(" where " + LISTED);
		Map<String, Object> params = new HashMap<>();
		params.put("now", now);
		if (criteria.query() != null && !criteria.query().isBlank()) {
			where.append(" and (e.title ilike :pattern escape '!' or e.venueName ilike :pattern escape '!')");
			params.put("pattern", "%" + escapeLike(criteria.query().trim()) + "%");
		}
		if (criteria.cityId() != null) {
			where.append(" and e.cityId = :cityId");
			params.put("cityId", criteria.cityId());
		}
		if (criteria.category() != null && !criteria.category().isBlank()) {
			where.append(" and e.category = :category");
			params.put("category", criteria.category().trim());
		}
		if (criteria.from() != null) {
			where.append(" and e.startsAt >= :from");
			params.put("from", criteria.from());
		}
		if (criteria.to() != null) {
			where.append(" and e.startsAt < :to");
			params.put("to", criteria.to());
		}

		long total = bind(em.createQuery("select count(e) from EventSnapshot e" + where, Long.class), params)
				.getSingleResult();
		List<Object[]> rows = bind(em.createQuery("""
				select e.eventId, e.title, e.bannerUrl, e.startsAt, e.venueName, e.cityName, e.currency, e.status,
				       (select min(i.priceMinor) from SeatInventory i where i.id.eventId = e.eventId)
				from EventSnapshot e
				""" + where + " order by e.startsAt, e.eventId", Object[].class), params)
				.setFirstResult(page * size).setMaxResults(size).getResultList();
		List<EventListItem> items = rows.stream()
				.map(row -> new EventListItem((UUID) row[0], (String) row[1], (String) row[2], (Instant) row[3],
						(String) row[4], (String) row[5], (Long) row[8], (String) row[6], (String) row[7]))
				.toList();
		return new EventPage(items, total, page, size);
	}

	public Optional<EventDetail> detail(UUID eventId) {
		List<Object[]> rows = em.createQuery("""
				select e.eventId, e.title, e.description, e.category, e.bannerUrl, e.organizerId, e.venueId, e.venueName,
				       e.cityName, e.startsAt, e.endsAt, e.saleStartsAt, e.status, e.highDemand, e.maxTicketsPerUser,
				       e.seatMapId, e.currency, e.doorsOpenAt, e.ageRestriction, e.rules
				from EventSnapshot e where e.eventId = :eventId
				""", Object[].class).setParameter("eventId", eventId).getResultList();
		return rows.stream().findFirst().map(row -> new EventDetail((UUID) row[0], (String) row[1], (String) row[2],
				(String) row[3], (String) row[4], (UUID) row[5], (UUID) row[6], (String) row[7], (String) row[8],
				(Instant) row[9], (Instant) row[10], (Instant) row[11], (String) row[12], (Boolean) row[13],
				((Short) row[14]).intValue(), (UUID) row[15], (String) row[16], (Instant) row[17], (Short) row[18],
				(String) row[19]));
	}

	public List<TierAvailability> tiers(UUID eventId) {
		List<Object[]> rows = em.createQuery("""
				select s.tierId, s.tierName, s.priceMinor, s.currency, count(s),
				       sum(case when s.status = 'AVAILABLE' then 1 else 0 end)
				from SeatInventory s where s.id.eventId = :eventId
				group by s.tierId, s.tierName, s.priceMinor, s.currency
				order by s.priceMinor desc, s.tierName
				""", Object[].class).setParameter("eventId", eventId).getResultList();
		return rows.stream().map(row -> new TierAvailability((UUID) row[0], (String) row[1], (Long) row[2],
				(String) row[3], ((Long) row[4]).intValue(), ((Number) row[5]).intValue())).toList();
	}

	public Map<UUID, SeatState> seatStates(UUID eventId) {
		List<Object[]> rows = em
				.createQuery("select s.id.seatId, s.tierId, s.status from SeatInventory s where s.id.eventId = :eventId",
						Object[].class)
				.setParameter("eventId", eventId).getResultList();
		return rows.stream().map(row -> new SeatState((UUID) row[0], (UUID) row[1], (String) row[2]))
				.collect(Collectors.toMap(SeatState::seatId, state -> state));
	}

	/** Hạng vé của từng khu (theo mã khu), suy ra từ tồn kho: khu không có ghế trong tồn kho là khu không bán. */
	public Map<String, UUID> tierBySectionCode(UUID eventId) {
		List<Object[]> rows = em.createQuery(
				"select distinct s.sectionCode, s.tierId from SeatInventory s where s.id.eventId = :eventId",
				Object[].class).setParameter("eventId", eventId).getResultList();
		Map<String, UUID> result = new HashMap<>();
		rows.forEach(row -> result.put((String) row[0], (UUID) row[1]));
		return result;
	}

	private static <T> TypedQuery<T> bind(TypedQuery<T> query, Map<String, Object> params) {
		params.forEach(query::setParameter);
		return query;
	}

	private static String escapeLike(String text) {
		StringBuilder escaped = new StringBuilder();
		for (char character : text.toCharArray()) {
			if (character == ESCAPE || character == '%' || character == '_') {
				escaped.append(ESCAPE);
			}
			escaped.append(character);
		}
		return escaped.toString();
	}

}
