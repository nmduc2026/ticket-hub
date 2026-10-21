package com.ndz.booking.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.dto.response.BookingViews.BookingItemView;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.LockedSeat;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.event.BookingConfirmationFailed;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.repository.EventSnapshotRepository;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.service.BookingConfirmationService;
import com.ndz.shared.util.Uuid7;

@Service
public class BookingConfirmationServiceImpl implements BookingConfirmationService {

	static final String CONSUMER = "booking.payment-succeeded";

	private static final Logger log = LoggerFactory.getLogger(BookingConfirmationServiceImpl.class);

	private final BookingRepository bookings;
	private final EventSnapshotRepository snapshots;
	private final ProcessedEventRepository processed;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	BookingConfirmationServiceImpl(BookingRepository bookings, EventSnapshotRepository snapshots,
			ProcessedEventRepository processed, ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
		this.snapshots = snapshots;
		this.processed = processed;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public void onPaymentSucceeded(PaymentSucceeded event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua PaymentSucceeded đã xử lý {}", event.messageId());
			return;
		}
		LockedBooking booking = bookings.lockBooking(event.bookingId()).orElse(null);
		if (booking == null) {
			// Có tiền mà không có booking: không tự sửa được, cần người xem (overview.md §16.3).
			log.error("PaymentSucceeded {} cho booking không tồn tại {}", event.paymentId(), event.bookingId());
			return;
		}
		switch (booking.status()) {
			case PENDING_PAYMENT -> confirm(booking);
			case EXPIRED, CANCELLED -> confirmLate(booking, event);
			case CONFIRMED -> log.info("Booking {} đã xác nhận, bỏ qua PaymentSucceeded {}", booking.id(),
					event.messageId());
			default -> log.error("PaymentSucceeded {} đến khi booking {} đang {}: không xử lý được", event.paymentId(),
					booking.id(), booking.status());
		}
	}

	private void confirm(LockedBooking booking) {
		booking.status().requireTransitionTo(BookingStatus.CONFIRMED);
		if (!bookings.transition(booking.id(), BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED)) {
			throw new IllegalStateException("Booking " + booking.id() + " đã bị chuyển trạng thái khi đang khoá");
		}
		ConfirmationData data = bookings.confirmationData(booking.id());
		int sold = bookings.markSold(booking.id());
		if (sold != data.items().size()) {
			// Bất biến BR-01: mọi ghế của booking đang chờ thanh toán phải đang HELD; sai thì hoàn tác để người xem.
			throw new IllegalStateException("Booking " + booking.id() + " có " + data.items().size()
					+ " ghế nhưng chỉ " + sold + " ghế đang giữ");
		}
		publishConfirmed(data);
		log.info("Booking {} đã xác nhận, {} ghế đã bán", booking.id(), sold);
	}

	/**
	 * Thanh toán đến muộn (§7.3, §7.7): giữ lại đúng các ghế của booking nếu sự kiện còn đang bán và mọi ghế còn
	 * {@code AVAILABLE}; được thì {@code EXPIRED / CANCELLED → CONFIRMED}, không thì giữ nguyên trạng thái và phát
	 * {@code BookingConfirmationFailed} để Payment hoàn 100%.
	 */
	private void confirmLate(LockedBooking booking, PaymentSucceeded event) {
		ConfirmationData data = bookings.confirmationData(booking.id());
		List<UUID> seatIds = data.items().stream().map(BookingItemView::seatId).toList();
		String failure = lateFailureReason(booking, seatIds);
		if (failure != null) {
			publisher.publishEvent(new BookingConfirmationFailed(Uuid7.next(clock), clock.instant(), booking.id(),
					event.paymentId(), failure));
			log.warn("Thanh toán {} đến muộn nhưng booking {} không xác nhận được ({}): hoàn tiền", event.paymentId(),
					booking.id(), failure);
			return;
		}
		booking.status().requireTransitionTo(BookingStatus.CONFIRMED);
		if (bookings.sellAvailableSeats(booking.eventId(), seatIds, booking.id()) != seatIds.size()
				|| !bookings.transition(booking.id(), booking.status(), BookingStatus.CONFIRMED)) {
			// Đã khoá ghế và booking nên không thể xảy ra; nếu xảy ra thì hoàn tác để người xem.
			throw new IllegalStateException("Không giữ lại được ghế của booking " + booking.id() + " dù đã khoá");
		}
		publishConfirmed(data);
		log.info("Thanh toán đến muộn: booking {} ({}) đã xác nhận lại {} ghế", booking.id(), booking.status(),
				seatIds.size());
	}

	/** Null nếu giữ lại được; ngược lại là lý do. Khoá ghế theo thứ tự {@code seat_id} như mọi luồng giữ ghế. */
	private String lateFailureReason(LockedBooking booking, List<UUID> seatIds) {
		boolean onSale = snapshots.rules(booking.eventId()).map(rules -> "ON_SALE".equals(rules.status()))
				.orElse(false);
		if (!onSale) {
			return BookingConfirmationFailed.EVENT_NOT_ON_SALE;
		}
		List<LockedSeat> seats = bookings.lockSeats(booking.eventId(), seatIds);
		boolean allAvailable = seats.size() == seatIds.size()
				&& seats.stream().allMatch(seat -> "AVAILABLE".equals(seat.status()));
		return allAvailable ? null : BookingConfirmationFailed.SEATS_UNAVAILABLE;
	}

	private void publishConfirmed(ConfirmationData data) {
		List<BookingConfirmed.Seat> seats = data.items().stream()
				.map(item -> new BookingConfirmed.Seat(item.seatId(), item.sectionCode(), item.rowLabel(),
						item.seatNumber(), item.tierId(), item.tierName(), item.priceMinor()))
				.toList();
		publisher.publishEvent(new BookingConfirmed(Uuid7.next(clock), clock.instant(), data.bookingId(),
				data.userId(), data.eventId(), data.eventTitle(), data.startsAt(), data.venueName(),
				data.contactEmail(), data.totalMinor(), data.currency(), seats));
	}

}
