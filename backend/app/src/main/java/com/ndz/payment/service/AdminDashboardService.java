package com.ndz.payment.service;

import com.ndz.payment.dto.response.AdminDashboardResponses.AdminDashboard;

/** Tổng quan vận hành cho Admin (màn #35, overview.md §11.2). */
public interface AdminDashboardService {

	/** {@code range}: số ngày gần nhất tính doanh thu / vé bán; không hợp lệ thì dùng 30. */
	AdminDashboard dashboard(int range);

}
