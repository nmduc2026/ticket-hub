package com.ndz.booking.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.ndz.booking.entity.SeatInventory;
import com.ndz.catalog.api.CatalogApi.SellableSeat;

/**
 * Ghi tồn kho theo lô: loại các ghế đã có rồi persist phần còn lại, flush / clear theo lô để persistence context không
 * phình khi sơ đồ có hàng chục nghìn ghế. Hai lần publish đồng thời không xảy ra vì event được khử trùng ở consumer.
 */
public class SeatInventoryRepositoryImpl implements SeatInventoryRepositoryCustom {

	private static final int BATCH_SIZE = 500;

	@PersistenceContext
	private EntityManager em;

	@Override
	public int insertAll(UUID eventId, List<SellableSeat> seats) {
		Set<UUID> existing = new HashSet<>(em
				.createQuery("select s.id.seatId from SeatInventory s where s.id.eventId = :eventId", UUID.class)
				.setParameter("eventId", eventId).getResultList());
		int added = 0;
		for (SellableSeat seat : seats) {
			if (!existing.add(seat.seatId())) {
				continue;
			}
			em.persist(new SeatInventory(eventId, seat.seatId(), seat.tierId(), seat.tierName(), seat.sectionCode(),
					seat.rowLabel(), seat.seatNumber(), seat.priceMinor(), seat.currency()));
			if (++added % BATCH_SIZE == 0) {
				em.flush();
				em.clear();
			}
		}
		em.flush();
		em.clear();
		return added;
	}

}
