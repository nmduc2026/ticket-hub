package com.ndz.payment.service.impl;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ndz.booking.event.PaymentSucceeded;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.enums.PaymentStatus;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.service.PaymentSettlementService;
import com.ndz.shared.util.Uuid7;

@Service
public class PaymentSettlementServiceImpl implements PaymentSettlementService {

	private static final Logger log = LoggerFactory.getLogger(PaymentSettlementServiceImpl.class);

	private final PaymentRepository payments;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	PaymentSettlementServiceImpl(PaymentRepository payments, ApplicationEventPublisher publisher, Clock clock) {
		this.payments = payments;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public boolean markSucceeded(Payment payment, Instant now) {
		if (!move(payment, PaymentStatus.SUCCEEDED, now)) {
			return false;
		}
		publisher.publishEvent(new PaymentSucceeded(Uuid7.next(clock), now, payment.getId(), payment.getBookingId(),
				payment.getAmountMinor(), payment.getCurrency()));
		log.info("Thanh toán {} của booking {} thành công", payment.getId(), payment.getBookingId());
		return true;
	}

	@Override
	public boolean markProcessing(Payment payment, Instant now) {
		return move(payment, PaymentStatus.PROCESSING, now);
	}

	@Override
	public boolean markCanceled(Payment payment, Instant now) {
		return move(payment, PaymentStatus.CANCELED, now);
	}

	private boolean move(Payment payment, PaymentStatus next, Instant now) {
		PaymentStatus before = payment.getStatus();
		if (!payment.transitionTo(next, now)) {
			log.warn("Payment {} đang {}, không chuyển được sang {}: bỏ qua", payment.getId(), before, next);
			return false;
		}
		payments.save(payment);
		return true;
	}

}
