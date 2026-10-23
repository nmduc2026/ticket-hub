package com.ndz.booking.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.booking.client.SeatUpdatePublisher;

/** Phát thay đổi ghế đã gom (overview.md §12). Mỗi pod phát thay đổi do chính nó ghi nhận nên không cần ShedLock. */
@Component
class FlushSeatUpdatesJob {

	private final SeatUpdatePublisher seatUpdates;

	FlushSeatUpdatesJob(SeatUpdatePublisher seatUpdates) {
		this.seatUpdates = seatUpdates;
	}

	@Scheduled(fixedDelayString = "${tickethub.booking.seat-update-interval:250ms}")
	void run() {
		seatUpdates.flush();
	}

}
