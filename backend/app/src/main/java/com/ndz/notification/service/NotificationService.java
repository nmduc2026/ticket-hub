package com.ndz.notification.service;

import com.ndz.booking.event.RefundCompleted;
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

}
