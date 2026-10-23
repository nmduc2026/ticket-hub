package com.ndz.notification.entity.enums;

/**
 * Loại thông báo trong app (overview.md §6.5). Loại bắt buộc luôn gửi: thông báo về vé, hoàn tiền, huỷ sự kiện và kết
 * quả duyệt hồ sơ organizer không tắt được trong cài đặt.
 */
public enum NotificationType {

	TICKET_ISSUED("Vé đã phát", true),
	REFUND_REQUESTED("Đã nhận yêu cầu hoàn vé", true),
	REFUND_COMPLETED("Đã hoàn tiền", true),
	REFUND_FAILED("Hoàn tiền cần xử lý", true),
	EVENT_CANCELLED("Sự kiện bị huỷ", true),
	SALE_PAUSED("Tạm dừng bán", false),
	EVENT_AUTO_SUSPENDED("Sự kiện tự tạm dừng", false),
	STRIPE_RESTRICTED("Stripe của organizer bị hạn chế", false),
	HOLD_EXPIRING("Sắp hết giờ giữ ghế", false),
	CUSTOMER_REFUND_REQUESTED("Khách yêu cầu hoàn vé", false),
	ORGANIZER_APPLICATION_PENDING("Hồ sơ organizer chờ duyệt", false),
	ORGANIZER_APPROVED("Hồ sơ organizer được duyệt", true),
	ORGANIZER_REJECTED("Hồ sơ organizer bị từ chối", true);

	private final String label;
	private final boolean mandatory;

	NotificationType(String label, boolean mandatory) {
		this.label = label;
		this.mandatory = mandatory;
	}

	/** Tên hiển thị trong màn cài đặt. */
	public String label() {
		return label;
	}

	/** Bắt buộc: không tắt được email lẫn trong app. */
	public boolean mandatory() {
		return mandatory;
	}

}
