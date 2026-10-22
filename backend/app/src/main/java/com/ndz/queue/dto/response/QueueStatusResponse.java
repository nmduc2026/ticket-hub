package com.ndz.queue.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Trạng thái của khách trong phòng chờ (overview.md §7.1).
 *
 * @param position vị trí từ 1; trống khi {@code LOTTERY} (chưa có vị trí cố định) hoặc {@code ADMITTED}
 * @param estimatedWaitSeconds thời gian chờ ước tính; trống cùng điều kiện với {@code position}
 * @param admissionExpiresAt hạn của lượt vào; chỉ có khi {@code ADMITTED}
 */
public record QueueStatusResponse(@Schema(nullable = true) Integer position,
		@Schema(nullable = true) Long estimatedWaitSeconds, Phase phase,
		@Schema(nullable = true) Instant admissionExpiresAt) {

	/** LOTTERY: vào trước giờ mở bán, thứ tự còn là bốc thăm; QUEUED: đang xếp hàng; ADMITTED: được vào chọn ghế. */
	public enum Phase {
		LOTTERY, QUEUED, ADMITTED
	}

}
