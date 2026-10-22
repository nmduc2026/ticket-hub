package com.ndz.catalog.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Nội dung organizer nhập cho một sự kiện. Chia hai nhóm theo BR-14: thông tin hiển thị (tiêu đề, mô tả, danh mục,
 * giờ mở cửa, độ tuổi, quy định) sửa được sau publish; thông tin bán vé (địa điểm, sơ đồ, giờ diễn, giờ mở bán, giới
 * hạn vé, bật phòng chờ) bị khoá sau publish (BR-04, BR-14).
 */
public record EventContent(String title, String description, String category, UUID venueId, UUID seatMapId,
		Instant startsAt, Instant endsAt, Instant saleStartsAt, short maxTicketsPerUser, Instant doorsOpenAt,
		Short ageRestriction, String rules, boolean highDemand) {

	boolean sameSalesInfoAs(Event event) {
		return java.util.Objects.equals(venueId, event.getVenueId())
				&& java.util.Objects.equals(seatMapId, event.getSeatMapId())
				&& java.util.Objects.equals(startsAt, event.getStartsAt())
				&& java.util.Objects.equals(endsAt, event.getEndsAt())
				&& java.util.Objects.equals(saleStartsAt, event.getSaleStartsAt())
				&& maxTicketsPerUser == event.getMaxTicketsPerUser() && highDemand == event.isHighDemand();
	}

}
