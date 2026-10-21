package com.ndz.booking.job;

import com.ndz.booking.service.BookingExpiryService;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job {@code ExpireBookings} (overview.md §7.3): 30 giây một lần, ShedLock để nhiều instance chỉ chạy một nơi. */
@Component
class ExpireBookingsJob {

	private static final Logger log = LoggerFactory.getLogger(ExpireBookingsJob.class);

	private final BookingExpiryService expiry;

	ExpireBookingsJob(BookingExpiryService expiry) {
		this.expiry = expiry;
	}

	@Scheduled(fixedDelayString = "${tickethub.booking.expire-interval:30s}")
	@SchedulerLock(name = "expire-bookings", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void run() {
		int expired = expiry.expireDueBookings();
		if (expired > 0) {
			log.info("ExpireBookings: {} booking hết hạn", expired);
		}
	}

	/** Báo "sắp hết giờ giữ ghế" (overview.md §6.5): cùng nhịp với job hết hạn để không trễ quá 30 giây. */
	@Scheduled(fixedDelayString = "${tickethub.booking.expire-interval:30s}")
	@SchedulerLock(name = "notify-expiring-holds", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
	void notifyExpiringHolds() {
		int notified = expiry.notifyExpiringHolds();
		if (notified > 0) {
			log.info("HoldExpiring: báo {} booking sắp hết giờ giữ ghế", notified);
		}
	}

}
