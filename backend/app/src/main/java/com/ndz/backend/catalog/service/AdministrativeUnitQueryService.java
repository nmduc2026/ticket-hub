package com.ndz.backend.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.backend.catalog.dto.response.CatalogViews.UnitRow;

/** Danh mục tỉnh / xã cho dropdown (overview.md §6.1, §11.2). Chỉ đọc. */
public interface AdministrativeUnitQueryService {

	List<UnitRow> units(Integer level, UUID parentId, String query);

}
