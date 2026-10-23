package com.ndz.booking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi.SellableSeat;

/** Phần của {@link SeatInventoryRepository} ghi theo lô. */
public interface SeatInventoryRepositoryCustom {

	/**
	 * Tạo tồn kho ghế, idempotent: ghế đã có (kể cả đang giữ hoặc đã bán) được giữ nguyên.
	 *
	 * @return số dòng thật sự được thêm
	 */
	@Transactional
	int insertAll(UUID eventId, List<SellableSeat> seats);

}
