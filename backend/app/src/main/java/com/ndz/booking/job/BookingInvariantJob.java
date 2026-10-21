package com.ndz.booking.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.ndz.booking.service.BookingInvariantService;

/** Cập nhật chỉ số đơn REFUND_FAILED định kỳ (overview.md §16.3); ShedLock để nhiều instance chỉ chạy một nơi. */
@Component
class BookingInvariantJob {

	private final BookingInvariantService invariants;

	BookingInvariantJob(BookingInvariantService invariants) {
		this.invariants = invariants;
	}

	@Scheduled(fixedDelayString = "${tickethub.booking.invariant-interval:60s}")
	@SchedulerLock(name = "booking-invariant-check", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void run() {
		invariants.checkRefundFailedBookings();
	}

}
