package com.ndz.payment.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.payment.dto.response.AdminOrderPaymentResponses.OrderPayment;
import com.ndz.payment.dto.response.AdminOrderPaymentResponses.OrderRefund;
import com.ndz.payment.dto.response.AdminOrderPaymentResponses.TimelineEntry;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.Refund;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.repository.RefundRepository;
import com.ndz.payment.repository.StripeEventRepository;
import com.ndz.payment.service.AdminOrderPaymentService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

@Service
class AdminOrderPaymentServiceImpl implements AdminOrderPaymentService {

	private final PaymentRepository payments;
	private final RefundRepository refunds;
	private final StripeEventRepository stripeEvents;

	AdminOrderPaymentServiceImpl(PaymentRepository payments, RefundRepository refunds, StripeEventRepository stripeEvents) {
		this.payments = payments;
		this.refunds = refunds;
		this.stripeEvents = stripeEvents;
	}

	@Override
	@Transactional(readOnly = true)
	public OrderPayment detail(UUID bookingId) {
		Payment payment = payments.findByBookingId(bookingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Đơn chưa có thanh toán"));
		List<Refund> refundRows = refunds.findByPaymentIdOrderByCreatedAt(payment.getId());

		List<TimelineEntry> timeline = new ArrayList<>();
		timeline.add(new TimelineEntry(payment.getCreatedAt(), "PAYMENT_CREATED", payment.getStripePaymentIntentId(), false));
		stripeEvents.findByPaymentIntent(payment.getStripePaymentIntentId()).forEach(event -> timeline
				.add(new TimelineEntry(event.receivedAt(), "STRIPE_EVENT", event.type(), event.processedAt() == null)));
		refundRows.forEach(refund -> timeline.add(
				new TimelineEntry(refund.getCreatedAt(), "REFUND", refund.getReason() + " " + refund.getStatus(), false)));
		timeline.sort(Comparator.comparing(TimelineEntry::at));

		return new OrderPayment(payment.getStatus().name(), payment.getAmountMinor(), payment.getApplicationFeeMinor(),
				payment.getCurrency(), payment.getStripePaymentIntentId(), payment.getDestinationAccountId(),
				refundRows.stream().map(refund -> new OrderRefund(refund.getReason(), refund.getStatus().name(),
						refund.getAmountMinor(), refund.getFailureReason(), refund.getCreatedAt())).toList(),
				timeline);
	}

}
