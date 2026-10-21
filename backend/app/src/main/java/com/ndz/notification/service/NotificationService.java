package com.ndz.notification.service;

import com.ndz.ticket.event.TicketIssued;

/** Gửi thông báo từ event nghiệp vụ (overview.md §6.5, §11.2). */
public interface NotificationService {

	/**
	 * Gửi email vé tới {@code contact_email} của booking. Idempotent theo {@code messageId}: nhận cùng event hai lần
	 * chỉ gửi một email. Gửi lỗi thì ghi {@code notification_log} FAILED rồi ném lỗi để event được thử lại. Người gọi
	 * (listener) mở transaction.
	 */
	void onTicketIssued(TicketIssued event);

}
