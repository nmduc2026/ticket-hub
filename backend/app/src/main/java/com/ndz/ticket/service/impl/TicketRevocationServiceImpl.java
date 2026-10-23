package com.ndz.ticket.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.event.TicketRevoked;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.repository.RevokedBookingRepository;
import com.ndz.ticket.repository.TicketProcessedEventRepository;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.TicketRevocationService;

@Service
public class TicketRevocationServiceImpl implements TicketRevocationService {

	static final String CONSUMER = "ticket.refund-requested";

	private static final Logger log = LoggerFactory.getLogger(TicketRevocationServiceImpl.class);

	private final TicketRepository tickets;
	private final RevokedBookingRepository revokedBookings;
	private final TicketProcessedEventRepository processed;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	TicketRevocationServiceImpl(TicketRepository tickets, RevokedBookingRepository revokedBookings,
			TicketProcessedEventRepository processed, ApplicationEventPublisher publisher, Clock clock) {
		this.tickets = tickets;
		this.revokedBookings = revokedBookings;
		this.processed = processed;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public void revokeFor(RefundRequested event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundRequested đã xử lý {}", event.messageId());
			return;
		}
		Instant now = clock.instant();
		revokedBookings.mark(event.bookingId(), now);
		List<Ticket> found = tickets.findByBookingId(event.bookingId());
		if (found.isEmpty()) {
			log.warn("Booking {} yêu cầu hoàn khi chưa có vé; vé phát sau sẽ bị thu hồi ngay", event.bookingId());
			return;
		}
		for (Ticket ticket : found) {
			// Phát cả cho vé đã thu hồi từ trước: Booking đếm theo vé và khử trùng, nên gửi thêm không hại.
			ticket.revoke();
			publisher.publishEvent(new TicketRevoked(Uuid7.next(clock), now, ticket.getId(), ticket.getBookingId(),
					ticket.getSeatId()));
		}
		tickets.saveAll(found);
		log.info("Thu hồi {} vé của booking {} (lý do {})", found.size(), event.bookingId(), event.reason());
	}

}
