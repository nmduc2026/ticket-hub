package com.ndz.catalog.service;

import java.util.UUID;

import com.ndz.catalog.dto.request.AdminEventFilter;
import com.ndz.catalog.dto.response.AdminEventResponses.AdminEventPage;

/** Danh sách sự kiện toàn hệ thống cho Admin (màn #34, overview.md §11.1, §11.2). */
public interface AdminEventListService {

	AdminEventPage list(AdminEventFilter filter, int page, int size);

	/** CSV theo cùng bộ lọc; quá giới hạn dòng ném {@code EXPORT_TOO_LARGE}. */
	String export(UUID adminId, AdminEventFilter filter);

}
