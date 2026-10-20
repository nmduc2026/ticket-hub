package com.ndz.backend.catalog.internal.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.backend.catalog.internal.domain.EventContent;

/**
 * Nội dung tạo / sửa sự kiện (gửi đủ mọi trường). Sau publish chỉ {@code title}, {@code description},
 * {@code category} được đổi; các trường còn lại phải giữ nguyên, nếu không trả {@code EVENT_LOCKED} (BR-14).
 */
@Schema(name = "EventRequest")
public record EventRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 5000) String description,
		@Size(max = 50) String category,
		UUID venueId,
		UUID seatMapId,
		Instant startsAt,
		Instant endsAt,
		Instant saleStartsAt,
		@Min(1) @Max(100) Short maxTicketsPerUser) {

	/** Mặc định 6 vé mỗi người cho mỗi sự kiện (BR-02). */
	private static final short DEFAULT_MAX_TICKETS_PER_USER = 6;

	EventContent toContent() {
		return new EventContent(title.trim(), description, category, venueId, seatMapId, startsAt, endsAt,
				saleStartsAt, maxTicketsPerUser == null ? DEFAULT_MAX_TICKETS_PER_USER : maxTicketsPerUser);
	}

}
