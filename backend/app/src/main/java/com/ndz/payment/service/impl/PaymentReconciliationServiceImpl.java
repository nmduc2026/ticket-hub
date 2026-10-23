package com.ndz.payment.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.booking.api.BookingApi;
import com.ndz.booking.event.PaymentReconcileFlagged;
import com.ndz.payment.client.GatewayPaymentIntent;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.client.StripeGatewayException;
import com.ndz.payment.config.PaymentProperties;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.enums.PaymentStatus;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.service.PaymentReconciliationService;
import com.ndz.payment.service.PaymentSettlementService;
import com.ndz.shared.util.Uuid7;

/**
 * Gọi Stripe ngoài transaction, rồi khoá dòng payment và áp dụng kết quả trong một transaction ngắn: không giữ khoá DB
 * trong lúc chờ dịch vụ ngoài, và webhook đến cùng lúc không chuyển trạng thái chồng lên nhau.
 */
@Service
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationServiceImpl.class);

	private static final Set<PaymentStatus> UNSETTLED = EnumSet.of(PaymentStatus.REQUIRES_PAYMENT,
			PaymentStatus.PROCESSING);

	private final PaymentRepository payments;
	private final PaymentSettlementService settlement;
	private final StripeGateway stripe;
	private final BookingApi bookings;
	private final PaymentProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;
	private final AtomicLong succeededUnsettled = new AtomicLong();

	PaymentReconciliationServiceImpl(PaymentRepository payments, PaymentSettlementService settlement,
			StripeGateway stripe, BookingApi bookings, PaymentProperties properties,
			PlatformTransactionManager transactions, ApplicationEventPublisher publisher, Clock clock,
			MeterRegistry metrics) {
		this.payments = payments;
		this.settlement = settlement;
		this.stripe = stripe;
		this.bookings = bookings;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactions);
		this.publisher = publisher;
		this.clock = clock;
		metrics.gauge("tickethub.payment.succeeded_unsettled", succeededUnsettled);
	}

	@Override
	public int reconcile() {
		Instant cutoff = clock.instant().minus(properties.reconcileAfter());
		List<Payment> due = payments.findByStatusInAndCreatedAtLessThanEqualOrderByCreatedAt(UNSETTLED, cutoff,
				PageRequest.of(0, properties.reconcileBatchSize()));
		int changed = 0;
		for (Payment payment : due) {
			try {
				if (reconcileOne(payment)) {
					changed++;
				}
				else {
					// Vẫn chưa chốt sau khi hỏi lại Stripe: cần Admin để ý (màn #29, overview.md §16.3).
					flagReconcile(payment);
				}
			}
			catch (StripeGatewayException e) {
				log.warn("Đối soát payment {}: không hỏi được Stripe (mã {}), thử lại ở lượt sau", payment.getId(),
						e.code());
				flagReconcile(payment);
			}
		}
		refreshUnsettledMetric();
		return changed;
	}

	/** Publish trong một transaction riêng: Modulith chỉ ghi {@code event_publication} khi có transaction đang mở. */
	private void flagReconcile(Payment payment) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(
				new PaymentReconcileFlagged(Uuid7.next(clock), clock.instant(), payment.getBookingId(), true)));
	}

	private boolean reconcileOne(Payment payment) {
		GatewayPaymentIntent intent = stripe.retrievePaymentIntent(payment.getStripePaymentIntentId());
		return Boolean.TRUE.equals(transaction.execute(status -> {
			Payment locked = payments.findByStripePaymentIntentId(payment.getStripePaymentIntentId()).orElseThrow();
			Instant now = clock.instant();
			boolean changed = switch (intent.status()) {
				case GatewayPaymentIntent.SUCCEEDED -> settlement.markSucceeded(locked, now);
				case "canceled" -> settlement.markCanceled(locked, now);
				case "processing" -> settlement.markProcessing(locked, now);
				default -> false; // còn chờ khách trả: không có gì để sửa
			};
			if (changed) {
				log.warn("Đối soát: payment {} lệch với Stripe (PaymentIntent {}), đã đưa về {}", locked.getId(),
						intent.status(), locked.getStatus());
			}
			return changed;
		}));
	}

	/**
	 * Số payment đã thu tiền quá {@code unsettledAlertAfter} mà booking chưa {@code CONFIRMED} và chưa được hoàn
	 * (overview.md §16.3: alert bất biến, cấu hình ở Sprint 4).
	 */
	private void refreshUnsettledMetric() {
		Instant cutoff = clock.instant().minus(properties.unsettledAlertAfter());
		List<Payment> candidates = payments.findSucceededWithoutRefundBefore(cutoff);
		Set<UUID> unconfirmed = bookings
				.notConfirmed(candidates.stream().map(Payment::getBookingId).collect(Collectors.toSet()));
		long count = candidates.stream().filter(payment -> unconfirmed.contains(payment.getBookingId())).count();
		succeededUnsettled.set(count);
		if (count > 0) {
			log.error("{} payment đã thu tiền quá {} mà booking chưa CONFIRMED và chưa được hoàn", count,
					properties.unsettledAlertAfter());
		}
	}

}
