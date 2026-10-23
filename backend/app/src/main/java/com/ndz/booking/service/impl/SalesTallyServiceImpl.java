package com.ndz.booking.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ndz.booking.event.TicketCheckedIn;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.repository.SalesRepository;
import com.ndz.booking.service.SalesTallyService;

@Service
public class SalesTallyServiceImpl implements SalesTallyService {

	static final String CHECKED_IN_CONSUMER = "booking.ticket-checked-in";

	private static final Logger log = LoggerFactory.getLogger(SalesTallyServiceImpl.class);

	private final ProcessedEventRepository processed;
	private final SalesRepository sales;

	SalesTallyServiceImpl(ProcessedEventRepository processed, SalesRepository sales) {
		this.processed = processed;
		this.sales = sales;
	}

	@Override
	public void onTicketCheckedIn(TicketCheckedIn event) {
		if (!processed.markProcessed(CHECKED_IN_CONSUMER, event.messageId())) {
			log.info("Bỏ qua TicketCheckedIn đã xử lý {}", event.messageId());
			return;
		}
		if (sales.recordCheckIn(event.eventId(), event.seatId(), event.occurredAt()) == 0) {
			log.warn("Check-in vé {}: không thấy ghế {} trong tồn kho sự kiện {}", event.ticketId(), event.seatId(),
					event.eventId());
		}
	}

}
