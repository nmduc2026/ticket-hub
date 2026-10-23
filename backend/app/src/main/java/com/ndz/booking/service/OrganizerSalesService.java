package com.ndz.booking.service;

import java.util.UUID;

import com.ndz.booking.dto.request.OrganizerBookingFilter;
import com.ndz.booking.dto.response.SalesResponses.EventSales;
import com.ndz.booking.dto.response.SalesResponses.OrganizerBookingPage;
import com.ndz.booking.dto.response.SalesResponses.OrganizerDashboard;

/**
 * Số liệu bán vé và đơn hàng của organizer (overview.md §6.2, màn #11, #14, #25). Chỉ trong sự kiện của chính
 * organizer: sự kiện của người khác trả {@code EVENT_NOT_FOUND} (404) như không tồn tại.
 */
public interface OrganizerSalesService {

	EventSales eventSales(UUID organizerId, UUID eventId);

	/** {@code days}: 7, 30 hoặc 90 ngày gần nhất (tính theo ngày Việt Nam, gồm hôm nay). */
	OrganizerDashboard dashboard(UUID organizerId, int days);

	OrganizerBookingPage bookings(UUID organizerId, OrganizerBookingFilter filter, int page, int size);

	/** CSV cùng bộ lọc, tối đa 10.000 dòng ({@code EXPORT_TOO_LARGE}), ghi audit. */
	String exportBookings(UUID organizerId, OrganizerBookingFilter filter);

}
