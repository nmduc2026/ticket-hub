package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.repository.CatalogReadRepository;

/** Cài đặt {@link CatalogApi} trong cùng process (giai đoạn modular monolith). */
@Service
class CatalogApiImpl implements CatalogApi {

	private final CatalogReadRepository catalog;

	CatalogApiImpl(CatalogReadRepository catalog) {
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
