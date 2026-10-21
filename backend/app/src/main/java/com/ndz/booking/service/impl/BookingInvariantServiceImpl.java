package com.ndz.booking.service.impl;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.entity.enums.BookingStatus;
import com.ndz.booking.repository.BookingRepository;
import com.ndz.booking.service.BookingInvariantService;

@Service
public class BookingInvariantServiceImpl implements BookingInvariantService {

	private static final Logger log = LoggerFactory.getLogger(BookingInvariantServiceImpl.class);

	private final BookingRepository bookings;
	private final AtomicInteger refundFailed = new AtomicInteger();

	BookingInvariantServiceImpl(BookingRepository bookings, MeterRegistry metrics) {
		this.bookings = bookings;
		metrics.gauge("tickethub.invariant.refund_failed_bookings", refundFailed);
	}

	@Override
	@Transactional(readOnly = true)
	public int checkRefundFailedBookings() {
		int count = (int) bookings.countByStatus(BookingStatus.REFUND_FAILED);
		refundFailed.set(count);
		if (count > 0) {
			log.error("{} đơn REFUND_FAILED đang chờ Admin hoàn thủ công", count);
		}
		return count;
	}

}
