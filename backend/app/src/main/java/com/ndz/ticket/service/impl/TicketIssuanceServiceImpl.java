package com.ndz.ticket.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.TicketRevoked;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.event.TicketIssued;
import com.ndz.ticket.repository.RevokedBookingRepository;
import com.ndz.ticket.repository.TicketProcessedEventRepository;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.TicketIssuanceService;
import com.ndz.ticket.util.QrTokenSigner;

@Service
public class TicketIssuanceServiceImpl implements TicketIssuanceService {

	static final String CONSUMER = "ticket.booking-confirmed";

	private static final Logger log = LoggerFactory.getLogger(TicketIssuanceServiceImpl.class);

	private final TicketRepository tickets;
	private final TicketProcessedEventRepository processed;
	private final RevokedBookingRepository revokedBookings;
	private final QrTokenSigner signer;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	TicketIssuanceServiceImpl(TicketRepository tickets, TicketProcessedEventRepository processed,
			RevokedBookingRepository revokedBookings, QrTokenSigner signer, ApplicationEventPublisher publisher, Clock clock) {
		this.tickets = tickets;
		this.processed = processed;
		this.revokedBookings = revokedBookings;
		this.signer = signer;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public void issueFor(BookingConfirmed event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua BookingConfirmed đã xử lý {}", event.messageId());
			return;
		}
		if (tickets.existsByBookingId(event.bookingId())) {
			log.warn("Booking {} đã có vé, không phát lại (BookingConfirmed {})", event.bookingId(),
					event.messageId());
			return;
		}
		Instant now = clock.instant();
		List<Ticket> issued = event.seats().stream().map(seat -> issue(event, seat, now)).toList();
		if (revokedBookings.isMarked(event.bookingId())) {
			// BR-09: khách đã yêu cầu hoàn trước khi vé kịp phát. Vé ra đời đã thu hồi, không gửi vé cho khách.
			issued.forEach(Ticket::revoke);
			tickets.saveAll(issued);
			issued.forEach(ticket -> publisher.publishEvent(new TicketRevoked(Uuid7.next(clock), now, ticket.getId(),
					ticket.getBookingId(), ticket.getSeatId())));
			log.warn("Booking {} đã yêu cầu hoàn trước khi phát vé: {} vé phát ra ở trạng thái thu hồi",
					event.bookingId(), issued.size());
			return;
		}
		// Index duy nhất (event_id, seat_id) chưa thu hồi là chốt chặn cuối: vi phạm thì lỗi ném ra và hoàn tác.
		tickets.saveAll(issued);
		publisher.publishEvent(new TicketIssued(Uuid7.next(clock), now, event.bookingId(), event.userId(),
				event.eventId(), event.contactEmail(), event.eventTitle(), event.startsAt(), event.venueName(),
				issued.stream().map(ticket -> new TicketIssued.Ticket(ticket.getId(), ticket.getSectionCode(),
						ticket.getRowLabel(), ticket.getSeatNumber(), ticket.getTierName())).toList()));
		log.info("Phát {} vé cho booking {}", issued.size(), event.bookingId());
	}

	private Ticket issue(BookingConfirmed event, BookingConfirmed.Seat seat, Instant now) {
		UUID id = Uuid7.next(clock);
		String label = seat.sectionCode() + "-" + seat.rowLabel() + "-" + seat.seatNumber();
		String token = signer.sign(id, event.eventId(), label);
		return new Ticket(id, event.bookingId(), event.eventId(), seat.seatId(), event.userId(), token,
				signer.keyId(), event.eventTitle(), event.startsAt(), event.venueName(), seat.sectionCode(),
				seat.rowLabel(), seat.seatNumber(), seat.tierName(), now);
	}

}
