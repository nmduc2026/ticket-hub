package com.ndz.payment.event.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.payment.event.PaymentFailed;

/**
 * Metric nghiệp vụ của Payment (overview.md §16.1): doanh thu gộp, tỉ lệ thanh toán thất bại, hoàn tiền xong / lỗi.
 * {@code tickethub.refund.failed} tăng nghĩa là có đơn cần Admin hoàn thủ công (alert bất biến, §16.3).
 */
@Component
class PaymentMetricsListener {

	private final Counter succeeded;
	private final Counter revenueMinor;
	private final Counter failed;
	private final Counter refundCompleted;
	private final Counter refundFailed;

	PaymentMetricsListener(MeterRegistry metrics) {
		this.succeeded = Counter.builder("tickethub.payment.succeeded").description("Thanh toán thành công")
				.register(metrics);
		this.revenueMinor = Counter.builder("tickethub.payment.revenue_minor")
				.description("Tổng tiền thu, đơn vị nhỏ nhất của tiền tệ").register(metrics);
		this.failed = Counter.builder("tickethub.payment.failed").description("Lần thanh toán thất bại")
				.register(metrics);
		this.refundCompleted = Counter.builder("tickethub.refund.completed").description("Hoàn tiền xong")
				.register(metrics);
		this.refundFailed = Counter.builder("tickethub.refund.failed").description("Hoàn tiền thất bại")
				.register(metrics);
	}

	@EventListener
	void on(PaymentSucceeded event) {
		succeeded.increment();
		revenueMinor.increment(event.amountMinor());
	}

	@EventListener
	void on(PaymentFailed event) {
		failed.increment();
	}

	@EventListener
	void on(RefundCompleted event) {
		refundCompleted.increment();
	}

	@EventListener
	void on(RefundFailed event) {
		refundFailed.increment();
	}

}
