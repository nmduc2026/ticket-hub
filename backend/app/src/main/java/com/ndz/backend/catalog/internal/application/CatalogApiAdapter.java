package com.ndz.backend.catalog.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.catalog.CatalogApi;
import com.ndz.backend.catalog.internal.persistence.CatalogReadRepository;

/** Cài đặt {@link CatalogApi} trong cùng process (giai đoạn modular monolith). */
@Service
class CatalogApiAdapter implements CatalogApi {

	private final CatalogReadRepository catalog;

	CatalogApiAdapter(CatalogReadRepository catalog) {
		this.catalog = catalog;
	}

	@Override
	@Transactional(readOnly = true)
	public SeatMapLayout seatMapLayout(UUID seatMapId) {
		return catalog.seatMapLayout(seatMapId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SellableSeat> sellableSeats(UUID eventId) {
		return catalog.sellableSeats(eventId);
	}

}
