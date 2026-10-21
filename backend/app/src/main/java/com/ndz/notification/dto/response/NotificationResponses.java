package com.ndz.notification.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response của API thông báo trong app (overview.md §11.2). */
public final class NotificationResponses {

	private NotificationResponses() {
	}

	@Schema(name = "Notification")
	public record NotificationItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(description = "TICKET_ISSUED, REFUND_REQUESTED, REFUND_COMPLETED, REFUND_FAILED, EVENT_CANCELLED, "
					+ "SALE_PAUSED hoặc HOLD_EXPIRING", requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String body,
			@Schema(description = "Đường dẫn trong app để mở khi bấm vào thông báo", nullable = true) String link,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(description = "Thời điểm đã đọc; vắng mặt nếu chưa đọc", nullable = true) Instant readAt) {
	}

	@Schema(name = "NotificationPage")
	public record NotificationPage(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NotificationItem> items,
			@Schema(description = "Con trỏ để lấy trang kế (tham số cursor); vắng mặt nếu hết", nullable = true)
			UUID nextCursor) {
	}

	@Schema(name = "UnreadCount")
	public record UnreadCount(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int count) {
	}

	@Schema(name = "NotificationPreference")
	public record PreferenceItem(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(description = "Tên hiển thị của loại", requiredMode = Schema.RequiredMode.REQUIRED) String label,
			@Schema(description = "Loại bắt buộc (vé, hoàn tiền, huỷ sự kiện) không tắt được",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean mandatory,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inApp) {
	}

}
