package com.ndz.booking.service;

import com.ndz.booking.event.TicketCheckedIn;

/** Đếm số vé đã check-in vào {@code sales_by_event} (overview.md §6.2); idempotent theo {@code processed_events}. */
public interface SalesTallyService {

	void onTicketCheckedIn(TicketCheckedIn event);

}
