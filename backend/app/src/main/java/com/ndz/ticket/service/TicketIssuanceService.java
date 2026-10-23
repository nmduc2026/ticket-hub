package com.ndz.ticket.service;

import com.ndz.booking.event.BookingConfirmed;

/** Phát vé khi booking được xác nhận (overview.md §7.2, §7.5). */
public interface TicketIssuanceService {

	/**
	 * Mỗi ghế một vé {@code VALID} kèm QR ký ES256, rồi phát {@code TicketIssued} một lần cho cả booking. Idempotent:
	 * nhận cùng event hai lần (hoặc booking đã có vé) thì không phát vé trùng và không phát lại. Người gọi (listener)
	 * mở transaction.
	 */
	void issueFor(BookingConfirmed event);

}
