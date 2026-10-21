package com.ndz.booking.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.booking.service.SeatHoldService;

/**
 * Kiểm tra Redis giữ ghế định kỳ (overview.md §10 "khi Redis chết"). Không dùng ShedLock: trạng thái "sẵn sàng" nằm
 * trong bộ nhớ từng instance nên mỗi instance phải tự kiểm tra và tự dựng lại key của mình.
 */
@Component
class SeatHoldProbeJob {

	private final SeatHoldService seatHolds;

	SeatHoldProbeJob(SeatHoldService seatHolds) {
		this.seatHolds = seatHolds;
	}

	@Scheduled(fixedDelayString = "${tickethub.booking.seat-hold.probe-interval:3s}")
	void run() {
		seatHolds.probe();
	}

}
