package com.ndz.payment.job;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ndz.payment.service.PaymentReconciliationService;

/** Job đối soát payment với Stripe (overview.md §7.3): 5 phút một lần, ShedLock để nhiều instance chỉ chạy một nơi. */
@Component
class PaymentReconciliationJob {

	private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

	private final PaymentReconciliationService reconciliation;

	PaymentReconciliationJob(PaymentReconciliationService reconciliation) {
		this.reconciliation = reconciliation;
	}

	@Scheduled(fixedDelayString = "${tickethub.payment.reconcile-interval:5m}")
	@SchedulerLock(name = "payment-reconciliation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
	void run() {
		int changed = reconciliation.reconcile();
		if (changed > 0) {
			log.warn("Đối soát: {} payment lệch với Stripe đã được sửa", changed);
		}
	}

}
