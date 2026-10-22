package com.ndz.payment.job;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.payment.service.PaymentRefundService;

/** Rút hàng đợi hoàn tiền khi huỷ sự kiện (BR-08): 5 giây một lượt, ShedLock để chỉ một pod chạy. */
@Component
class RefundQueueJob {

	private static final Logger log = LoggerFactory.getLogger(RefundQueueJob.class);

	private final PaymentRefundService refunds;

	RefundQueueJob(PaymentRefundService refunds) {
		this.refunds = refunds;
	}

	@Scheduled(fixedDelayString = "${tickethub.payment.refund-queue-interval:5s}")
	@SchedulerLock(name = "refund-queue", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
	void run() {
		int done = refunds.processRefundQueue();
		if (done > 0) {
			log.info("RefundQueue: gửi {} lệnh hoàn", done);
		}
	}

}
