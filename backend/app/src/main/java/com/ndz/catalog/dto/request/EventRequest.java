package com.ndz.catalog.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Nội dung tạo / sửa sự kiện (gửi đủ mọi trường). Sau publish chỉ {@code title}, {@code description},
 * {@code category}, {@code doorsOpenAt}, {@code ageRestriction}, {@code rules} được đổi; các trường còn lại (kể cả
 * {@code highDemand}) phải giữ nguyên, nếu không trả {@code EVENT_LOCKED} (BR-14).
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
		@Min(1) @Max(100) Short maxTicketsPerUser,
		@Schema(description = "Bật phòng chờ cho sự kiện nhu cầu cao (BR-04); trống là tắt. Khoá sau publish (BR-14)") Boolean highDemand,
		@Schema(description = "Giờ mở cửa, không được sau giờ diễn") Instant doorsOpenAt,
		@Schema(description = "Số tuổi tối thiểu; trống là không giới hạn") @Min(1) @Max(99) Short ageRestriction,
		@Schema(description = "Quy định khi tham dự (văn bản)") @Size(max = 5000) String rules) {

}
