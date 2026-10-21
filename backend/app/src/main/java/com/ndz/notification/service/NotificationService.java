package com.ndz.notification.service;

import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.booking.event.BookingSalePaused;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.ticket.event.TicketIssued;

/** Gửi thông báo từ event nghiệp vụ (overview.md §6.5, §11.2). */
public interface NotificationService {

	/**
	 * Gửi email vé tới {@code contact_email} của booking. Idempotent theo {@code messageId}: nhận cùng event hai lần
	 * chỉ gửi một email. Gửi lỗi thì ghi {@code notification_log} FAILED rồi ném lỗi để event được thử lại. Người gọi
	 * (listener) mở transaction.
	 */
	void onTicketIssued(TicketIssued event);

	/**
	 * Gửi email xác nhận đã hoàn tiền. Hoàn không có người nhận (hoàn tự động vì thanh toán đến muộn) thì bỏ qua.
	 * Idempotent theo {@code messageId}; gửi lỗi xử lý như {@link #onTicketIssued}.
	 */
	void onRefundCompleted(RefundCompleted event);

	/** Email "sắp hết giờ giữ ghế": loại tuỳ chọn, chỉ gửi khi khách bật email cho loại này. */
	void onHoldExpiring(BookingHoldExpiring event);

	/** Email "tạm dừng bán" cho khách có đơn (BR-13): loại tuỳ chọn, theo cờ email. */
	void onSalePaused(BookingSalePaused event);

	/**
	 * Gửi email kết quả duyệt hồ sơ organizer tới email liên hệ trong hồ sơ. Idempotent theo {@code messageId}; gửi lỗi
	 * xử lý như {@link #onTicketIssued}.
	 */
	void onOrganizerApproved(OrganizerApproved event);

	/** Như {@link #onOrganizerApproved}, kèm lý do từ chối. */
	void onOrganizerRejected(OrganizerRejected event);

}
