package com.ndz.notification.service;

import java.util.List;
import java.util.UUID;

import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.catalog.event.OrganizerApplied;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.notification.dto.request.UpdatePreferencesRequest;
import com.ndz.notification.dto.response.NotificationResponses.NotificationPage;
import com.ndz.notification.dto.response.NotificationResponses.PreferenceItem;
import com.ndz.notification.entity.enums.NotificationType;
import com.ndz.ticket.event.TicketIssued;

/** Hộp thư thông báo trong app (overview.md §6.5, §11.2): tạo từ event, xem và đánh dấu đã đọc, cài đặt. */
public interface InAppNotificationService {

	/**
	 * Tạo thông báo cho {@code userId} nếu người này không tắt loại đó. Idempotent theo {@code (userId, sourceEventId)}:
	 * gửi lại cùng event không tạo thêm. Loại bắt buộc luôn được tạo.
	 *
	 * @return true nếu đã tạo thông báo mới
	 */
	boolean notify(UUID userId, NotificationType type, String title, String body, String link, UUID sourceEventId);

	/** Vé đã phát → "Vé của bạn đã sẵn sàng". */
	void onTicketIssued(TicketIssued event);

	/** Đã nhận yêu cầu hoàn → báo số tiền sẽ nhận lại. */
	void onRefundRequested(RefundRequested event);

	/** Khách: "sắp hết giờ giữ ghế", dẫn về trang thanh toán của booking. */
	void onHoldExpiring(BookingHoldExpiring event);

	/** Đã hoàn tiền xong; hoàn tự động không có người nhận thì bỏ qua. */
	void onRefundCompleted(RefundCompleted event);

	/** Hoàn tiền lỗi, đang được xử lý thủ công. */
	void onRefundFailed(RefundFailed event);

	/** Hồ sơ organizer mới → mỗi Admin còn hoạt động nhận một thông báo "chờ duyệt". */
	void onOrganizerApplied(OrganizerApplied event);

	/** Hồ sơ được duyệt → báo organizer. */
	void onOrganizerApproved(OrganizerApproved event);

	/** Hồ sơ bị từ chối → báo organizer kèm lý do. */
	void onOrganizerRejected(OrganizerRejected event);

	/** Hộp thư của chính người dùng, mới nhất trước, theo con trỏ. */
	NotificationPage list(UUID userId, boolean unreadOnly, UUID cursor, int size);

	int unreadCount(UUID userId);

	/**
	 * Đánh dấu đã đọc một thông báo của chính mình; đã đọc rồi vẫn thành công.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code NOTIFICATION_NOT_FOUND} nếu không phải của mình
	 */
	void markRead(UUID userId, UUID notificationId);

	/** Đánh dấu mọi thông báo của mình là đã đọc; trả số thông báo vừa đổi. */
	int markAllRead(UUID userId);

	/** Mọi loại thông báo kèm lựa chọn hiện tại (mặc định bật cả hai kênh). */
	List<PreferenceItem> preferences(UUID userId);

	/**
	 * Lưu lựa chọn theo loại.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code NOTIFICATION_MANDATORY} khi tắt loại bắt buộc,
	 *         {@code UNKNOWN_NOTIFICATION_TYPE} khi loại không có; lỗi thì không lưu gì
	 */
	List<PreferenceItem> updatePreferences(UUID userId, UpdatePreferencesRequest request);

	/** Xoá thông báo cũ hơn thời hạn giữ (90 ngày); trả số bản đã xoá. */
	int purgeExpired();

}
