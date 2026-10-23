package com.ndz.booking.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bộ lọc danh sách đơn của organizer (màn #14); mọi trường có thể trống. {@code q} tìm theo email hoặc đầu mã đơn;
 * {@code from} / {@code to} theo giờ tạo đơn.
 */
public record OrganizerBookingFilter(UUID eventId, List<String> statuses, Instant from, Instant to, String q) {

	public static OrganizerBookingFilter none() {
		return new OrganizerBookingFilter(null, null, null, null, null);
	}

}
