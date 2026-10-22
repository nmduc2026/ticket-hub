package com.ndz.account.service;

import java.util.UUID;

import com.ndz.account.dto.request.AdminAuditLogFilter;
import com.ndz.account.dto.response.AdminAuditLogResponses.AdminAuditLogPage;

/** Nhật ký thao tác quản trị cho Admin (màn #31, overview.md §13.4). */
public interface AdminAuditLogService {

	AdminAuditLogPage list(AdminAuditLogFilter filter, int page, int size);

	/** CSV theo cùng bộ lọc; quá giới hạn dòng ném {@code EXPORT_TOO_LARGE}. */
	String export(UUID adminId, AdminAuditLogFilter filter);

}
