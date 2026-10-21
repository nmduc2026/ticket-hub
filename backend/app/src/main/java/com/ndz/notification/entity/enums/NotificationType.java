package com.ndz.notification.entity.enums;

/**
 * Loại thông báo trong app (overview.md §6.5). Loại bắt buộc luôn gửi: thông báo về vé, hoàn tiền và huỷ sự kiện
 * không tắt được trong cài đặt.
 */
public enum NotificationType {

	TICKET_ISSUED("Vé đã phát", true),
	REFUND_REQUESTED("Đã nhận yêu cầu hoàn vé", true),
	REFUND_COMPLETED("Đã hoàn tiền", true),
	REFUND_FAILED("Hoàn tiền cần xử lý", true),
	EVENT_CANCELLED("Sự kiện bị huỷ", true),
	SALE_PAUSED("Tạm dừng bán", false),
	HOLD_EXPIRING("Sắp hết giờ giữ ghế", false);

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
