package com.ndz.booking.event.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.BookingCancelled;
import com.ndz.booking.event.BookingConfirmed;
import com.ndz.booking.event.BookingCreated;
import com.ndz.booking.event.BookingExpired;
import com.ndz.booking.event.RefundRequested;

/**
 * Metric nghiệp vụ của Booking (overview.md §16.1): đếm theo event mà module này phát. Dùng {@code @EventListener}
 * thường (không phải listener của Modulith) nên không tạo bản ghi {@code event_publication}; đếm dư rất hiếm khi
 * transaction phát event bị hoàn tác, chấp nhận được với metric.
 */
@Component
class BookingMetricsListener {

	private final Counter held;
	private final Counter expired;
	private final Counter cancelled;
	private final Counter confirmed;
	private final Counter ticketsSold;
	private final Counter refundRequested;

	BookingMetricsListener(MeterRegistry metrics) {
		this.held = Counter.builder("tickethub.booking.held").description("Booking giữ ghế thành công")
				.register(metrics);
		this.expired = Counter.builder("tickethub.booking.expired").description("Booking hết hạn giữ ghế")
				.register(metrics);
		this.cancelled = Counter.builder("tickethub.booking.cancelled").description("Booking khách tự huỷ")
				.register(metrics);
		this.confirmed = Counter.builder("tickethub.booking.confirmed").description("Booking đã thanh toán xong")
				.register(metrics);
		this.ticketsSold = Counter.builder("tickethub.tickets.sold").description("Số ghế đã bán").register(metrics);
		this.refundRequested = Counter.builder("tickethub.booking.refund_requested")
				.description("Booking được yêu cầu hoàn").register(metrics);
	}

	@EventListener
	void on(BookingCreated event) {
		held.increment();
	}

	@EventListener
	void on(BookingExpired event) {
		expired.increment();
	}

	@EventListener
	void on(BookingCancelled event) {
		cancelled.increment();
	}

	@EventListener
	void on(BookingConfirmed event) {
		confirmed.increment();
		ticketsSold.increment(event.seats().size());
	}

	@EventListener
	void on(RefundRequested event) {
		refundRequested.increment();
	}

}
