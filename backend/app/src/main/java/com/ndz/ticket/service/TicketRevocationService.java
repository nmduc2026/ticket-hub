package com.ndz.ticket.service;

import com.ndz.booking.event.RefundRequested;

/** Thu hồi vé khi booking yêu cầu hoàn (overview.md §7.4, BR-09, ADR 0026). */
public interface TicketRevocationService {

	/**
	 * Thu hồi mọi vé của booking ngay, không chờ tiền hoàn, và phát {@code TicketRevoked} cho từng vé. Booking chưa có vé
	 * (vé chưa kịp phát) thì ghi nhận để vé phát sau ra đời đã bị thu hồi. Idempotent. Người gọi (listener) mở
	 * transaction.
	 */
	void revokeFor(RefundRequested event);

}
