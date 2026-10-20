package com.ndz.backend.catalog.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.catalog.internal.domain.CatalogViews.UnitRow;
import com.ndz.backend.catalog.internal.persistence.CatalogReadRepository;

/** Danh mục tỉnh / xã cho dropdown (overview.md §6.1, §11.2). Chỉ đọc. */
@Service
public class AdministrativeUnitQueryService {

	private final CatalogReadRepository catalog;

	AdministrativeUnitQueryService(CatalogReadRepository catalog) {
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public List<UnitRow> units(Integer level, UUID parentId, String query) {
		return catalog.activeUnits(level, parentId, query);
	}

}
