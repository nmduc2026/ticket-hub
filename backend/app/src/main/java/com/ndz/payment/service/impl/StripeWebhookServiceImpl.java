package com.ndz.payment.service.impl;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.payment.client.GatewayWebhookEvent;
import com.ndz.payment.client.InvalidWebhookException;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.enums.PaymentStatus;
import com.ndz.payment.event.PaymentFailed;
import com.ndz.payment.event.PaymentSucceeded;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.repository.StripeEventRepository;
import com.ndz.payment.service.StripeWebhookService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

/**
 * Ghi {@code stripe_events}, đổi trạng thái payment và phát event trong cùng một transaction: lỗi giữa chừng thì cả ba
 * cùng hoàn tác và Stripe gửi lại; gửi trùng thì lần sau thấy bản ghi đã có và bỏ qua.
 */
@Service
public class StripeWebhookServiceImpl implements StripeWebhookService {

	private static final Logger log = LoggerFactory.getLogger(StripeWebhookServiceImpl.class);

	private final StripeGateway stripe;
	private final StripeEventRepository stripeEvents;
	private final PaymentRepository payments;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	StripeWebhookServiceImpl(StripeGateway stripe, StripeEventRepository stripeEvents, PaymentRepository payments,
			ApplicationEventPublisher publisher, Clock clock) {
		this.stripe = stripe;
		this.stripeEvents = stripeEvents;
		this.payments = payments;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void handle(String payload, String signatureHeader) {
		GatewayWebhookEvent event = verify(payload, signatureHeader);
		Instant now = clock.instant();
		if (!stripeEvents.insertIfAbsent(event.id(), event.type(), payload, now)) {
			log.info("Bỏ qua webhook đã nhận {} ({})", event.id(), event.type());
			return;
		}
		apply(event, now);
		stripeEvents.markProcessed(event.id(), now);
	}

	private GatewayWebhookEvent verify(String payload, String signatureHeader) {
		try {
			return stripe.parseWebhook(payload, signatureHeader);
		}
		catch (InvalidWebhookException e) {
			log.warn("Từ chối webhook: {}", e.getMessage());
			throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook không hợp lệ");
		}
	}

	private void apply(GatewayWebhookEvent event, Instant now) {
		if (event.paymentIntentId() == null) {
			log.debug("Bỏ qua webhook không thuộc PaymentIntent: {} ({})", event.id(), event.type());
			return;
		}
		Payment payment = payments.findByStripePaymentIntentId(event.paymentIntentId()).orElse(null);
		if (payment == null) {
			log.warn("Webhook {} ({}) cho PaymentIntent không có payment nào: bỏ qua", event.id(), event.type());
			return;
		}
		switch (event.type()) {
			case GatewayWebhookEvent.PAYMENT_INTENT_PROCESSING -> transition(payment, PaymentStatus.PROCESSING, event, now);
			case GatewayWebhookEvent.PAYMENT_INTENT_SUCCEEDED -> succeeded(payment, event, now);
			case GatewayWebhookEvent.PAYMENT_INTENT_PAYMENT_FAILED -> failed(payment, event, now);
			default -> log.debug("Bỏ qua loại webhook chưa xử lý: {}", event.type());
		}
	}

	private void succeeded(Payment payment, GatewayWebhookEvent event, Instant now) {
		if (transition(payment, PaymentStatus.SUCCEEDED, event, now)) {
			publisher.publishEvent(new PaymentSucceeded(Uuid7.next(clock), now, payment.getId(),
					payment.getBookingId(), payment.getAmountMinor(), payment.getCurrency()));
			log.info("Thanh toán {} của booking {} thành công", payment.getId(), payment.getBookingId());
		}
	}

	/**
	 * "Thanh toán thất bại" là một sự việc, không phải trạng thái: đang {@code REQUIRES_PAYMENT} thì giữ nguyên, đang
	 * {@code PROCESSING} thì quay về {@code REQUIRES_PAYMENT} để khách thử thẻ khác. Đã {@code SUCCEEDED} / {@code
	 * CANCELED} thì event này sai thứ tự, bỏ qua.
	 */
	private void failed(Payment payment, GatewayWebhookEvent event, Instant now) {
		PaymentStatus current = payment.getStatus();
		if (current == PaymentStatus.PROCESSING) {
			transition(payment, PaymentStatus.REQUIRES_PAYMENT, event, now);
		}
		else if (current != PaymentStatus.REQUIRES_PAYMENT) {
			ignoreOutOfOrder(payment, event);
			return;
		}
		publisher.publishEvent(new PaymentFailed(Uuid7.next(clock), now, payment.getId(), payment.getBookingId(),
				event.failureCode()));
		log.info("Thanh toán {} của booking {} thất bại ({})", payment.getId(), payment.getBookingId(),
				event.failureCode());
	}

	private boolean transition(Payment payment, PaymentStatus next, GatewayWebhookEvent event, Instant now) {
		PaymentStatus before = payment.getStatus();
		if (payment.transitionTo(next, now)) {
			payments.save(payment);
			return true;
		}
		log.warn("Bỏ qua webhook {} ({}) sai thứ tự: payment {} đang {}, không chuyển được sang {}", event.id(),
				event.type(), payment.getId(), before, next);
		return false;
	}

	private void ignoreOutOfOrder(Payment payment, GatewayWebhookEvent event) {
		log.warn("Bỏ qua webhook {} ({}) sai thứ tự: payment {} đang {}", event.id(), event.type(), payment.getId(),
				payment.getStatus());
	}

}
