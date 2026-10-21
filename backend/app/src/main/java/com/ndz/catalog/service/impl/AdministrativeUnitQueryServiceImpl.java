package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.response.CatalogViews.UnitRow;
import com.ndz.catalog.service.AdministrativeUnitQueryService;
import com.ndz.catalog.repository.CatalogReadRepository;

/** Danh mục tỉnh / xã cho dropdown (overview.md §6.1, §11.2). Chỉ đọc. */
@Service
public class AdministrativeUnitQueryServiceImpl implements AdministrativeUnitQueryService {

	private final CatalogReadRepository catalog;

	AdministrativeUnitQueryServiceImpl(CatalogReadRepository catalog) {
		this.catalog = catalog;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UnitRow> units(Integer level, UUID parentId, String query) {
		return catalog.activeUnits(level, parentId, query);
	}

}
