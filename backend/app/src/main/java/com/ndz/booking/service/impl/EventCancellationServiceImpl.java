package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.response.BookingResponses.RefundProgress;
import com.ndz.booking.dto.response.BookingViews.AffectedBooking;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.event.BookingCancelled;
import com.ndz.booking.event.RefundRequested;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.client.RedisEventDetailCache;
import com.ndz.booking.repository.EventSnapshotRepository;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.service.EventCancellationService;
import com.ndz.booking.service.SeatHoldService;
import com.ndz.catalog.event.EventCancelled;
import com.ndz.shared.util.Uuid7;

@Service
public class EventCancellationServiceImpl implements EventCancellationService {

	static final String CONSUMER = "booking.event-cancelled";

	private static final Logger log = LoggerFactory.getLogger(EventCancellationServiceImpl.class);

	private final BookingRepository bookings;
	private final EventSnapshotRepository snapshots;
	private final ProcessedEventRepository processed;
	private final SeatHoldService seatHolds;
	private final ApplicationEventPublisher publisher;
	private final RedisEventDetailCache detailCache;
	private final Clock clock;

	EventCancellationServiceImpl(BookingRepository bookings, EventSnapshotRepository snapshots,
			ProcessedEventRepository processed, SeatHoldService seatHolds, ApplicationEventPublisher publisher,
			RedisEventDetailCache detailCache, Clock clock) {
		this.bookings = bookings;
		this.snapshots = snapshots;
		this.processed = processed;
		this.seatHolds = seatHolds;
		this.publisher = publisher;
		this.detailCache = detailCache;
		this.clock = clock;
	}

	@Override
	public void onEventCancelled(EventCancelled event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua EventCancelled đã xử lý {}", event.messageId());
			return;
		}
		if (!snapshots.updateStatus(event.eventId(), "CANCELLED", event.occurredAt()) && !snapshots.exists(event.eventId())) {
			throw new IllegalStateException("EventCancelled đến trước EventPublished của sự kiện " + event.eventId());
		}
		detailCache.evictAfterCommit(event.eventId());
		Instant now = clock.instant();
		List<AffectedBooking> affected = bookings.affectedBySalePause(event.eventId());
		int cancelled = 0;
		int refunding = 0;
		for (AffectedBooking booking : affected) {
			if (BookingStatus.PENDING_PAYMENT.name().equals(booking.status())) {
				cancelled += cancelPending(booking, event.eventId(), now) ? 1 : 0;
			}
			else {
				refunding += requestFullRefund(booking, now) ? 1 : 0;
			}
		}
		log.warn("Sự kiện {} bị huỷ: huỷ {} đơn chờ thanh toán, hoàn 100% {} đơn", event.eventId(), cancelled, refunding);
	}

	@Override
	@Transactional(readOnly = true)
	public RefundProgress refundProgress(UUID eventId) {
		return bookings.refundProgress(eventId, RefundRequested.EVENT_CANCELLED);
	}

	private boolean cancelPending(AffectedBooking booking, UUID eventId, Instant now) {
		if (!bookings.transition(booking.bookingId(), BookingStatus.PENDING_PAYMENT, BookingStatus.CANCELLED)) {
			return false;
		}
		List<UUID> seatIds = bookings.releaseSeats(booking.bookingId());
		seatHolds.releaseAfterCommit(eventId, seatIds, booking.bookingId());
		// Payment huỷ PaymentIntent; tiền đến sau vẫn được hoàn 100% (§7.3 thanh toán đến muộn).
		publisher.publishEvent(new BookingCancelled(Uuid7.next(clock), now, booking.bookingId(), booking.userId(),
				eventId, seatIds));
		return true;
	}

	/** BR-08: hoàn toàn bộ số đã trả, gồm phí nền tảng, không xét mốc 48 giờ. */
	private boolean requestFullRefund(AffectedBooking booking, Instant now) {
		ConfirmationData data = bookings.confirmationData(booking.bookingId());
		if (!bookings.markRefundRequested(booking.bookingId(), RefundRequested.EVENT_CANCELLED, data.totalMinor(), now)) {
			return false;
		}
		publisher.publishEvent(new RefundRequested(Uuid7.next(clock), now, booking.bookingId(), booking.userId(),
				data.eventId(), data.eventTitle(), data.contactEmail(), RefundRequested.EVENT_CANCELLED,
				data.totalMinor(), data.currency(), null));
		return true;
	}

}
