package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.response.CatalogViews.UnitRow;
import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.service.AdministrativeUnitQueryService;
import com.ndz.catalog.util.AdministrativeUnitCache;

/** Danh mục tỉnh / xã cho dropdown (overview.md §6.1, §11.2). Chỉ đọc; truy vấn không có từ khoá đi qua cache. */
@Service
public class AdministrativeUnitQueryServiceImpl implements AdministrativeUnitQueryService {

	private final CatalogReadRepository catalog;
	private final AdministrativeUnitCache cache;

	AdministrativeUnitQueryServiceImpl(CatalogReadRepository catalog, AdministrativeUnitCache cache) {
		this.catalog = catalog;
		this.cache = cache;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UnitRow> units(Integer level, UUID parentId, String query) {
		if (query != null && !query.isBlank()) {
			return catalog.activeUnits(level, parentId, query);
		}
		return cache.get(level, parentId, () -> catalog.activeUnits(level, parentId, null));
	}

}
