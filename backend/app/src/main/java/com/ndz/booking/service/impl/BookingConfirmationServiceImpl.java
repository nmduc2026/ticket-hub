package com.ndz.booking.service.impl;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.repository.ProcessedEventRepository;
import com.ndz.booking.service.BookingConfirmationService;
import com.ndz.shared.util.Uuid7;

@Service
public class BookingConfirmationServiceImpl implements BookingConfirmationService {

	static final String CONSUMER = "booking.payment-succeeded";

	private static final Logger log = LoggerFactory.getLogger(BookingConfirmationServiceImpl.class);

	private final BookingRepository bookings;
	private final ProcessedEventRepository processed;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	BookingConfirmationServiceImpl(BookingRepository bookings, ProcessedEventRepository processed,
			ApplicationEventPublisher publisher, Clock clock) {
		this.bookings = bookings;
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
			case CONFIRMED -> log.info("Booking {} đã xác nhận, bỏ qua PaymentSucceeded {}", booking.id(),
					event.messageId());
			default -> log.error("PaymentSucceeded {} đến khi booking {} đang {}: chưa có nhánh xử lý",
					event.paymentId(), booking.id(), booking.status());
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
		List<BookingConfirmed.Seat> seats = data.items().stream()
				.map(item -> new BookingConfirmed.Seat(item.seatId(), item.sectionCode(), item.rowLabel(),
						item.seatNumber(), item.tierId(), item.tierName(), item.priceMinor()))
				.toList();
		publisher.publishEvent(new BookingConfirmed(Uuid7.next(clock), clock.instant(), data.bookingId(),
				data.userId(), data.eventId(), data.eventTitle(), data.startsAt(), data.venueName(),
				data.contactEmail(), data.totalMinor(), data.currency(), seats));
		log.info("Booking {} đã xác nhận, {} ghế đã bán", booking.id(), sold);
	}

}
