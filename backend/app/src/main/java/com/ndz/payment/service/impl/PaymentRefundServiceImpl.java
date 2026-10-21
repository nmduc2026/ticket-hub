package com.ndz.payment.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.payment.client.GatewayRefund;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.Refund;
import com.ndz.payment.entity.enums.PaymentStatus;
import com.ndz.payment.entity.enums.RefundStatus;
import com.ndz.payment.repository.PaymentProcessedEventRepository;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.repository.RefundRepository;
import com.ndz.payment.service.PaymentRefundService;
import com.ndz.shared.util.Uuid7;

/**
 * Bản ghi hoàn tiền được ghi và commit trong transaction riêng trước khi gọi Stripe: nếu gọi Stripe lỗi sau khi Stripe
 * đã nhận lệnh, lần thử lại dùng đúng bản ghi và idempotency key đó thay vì tạo lần hoàn mới (không hoàn hai lần).
 */
@Service
public class PaymentRefundServiceImpl implements PaymentRefundService {

	static final String CONSUMER = "payment.confirmation-failed";

	private static final Logger log = LoggerFactory.getLogger(PaymentRefundServiceImpl.class);

	private final PaymentRepository payments;
	private final RefundRepository refunds;
	private final PaymentProcessedEventRepository processed;
	private final StripeGateway stripe;
	private final TransactionTemplate newTransaction;
	private final Clock clock;

	PaymentRefundServiceImpl(PaymentRepository payments, RefundRepository refunds,
			PaymentProcessedEventRepository processed, StripeGateway stripe, PlatformTransactionManager transactions,
			Clock clock) {
		this.payments = payments;
		this.refunds = refunds;
		this.processed = processed;
		this.stripe = stripe;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@Override
	public void refundFully(UUID messageId, UUID paymentId, String reason) {
		if (!processed.markProcessed(CONSUMER, messageId)) {
			log.info("Bỏ qua yêu cầu hoàn đã xử lý {}", messageId);
			return;
		}
		Payment payment = payments.findById(paymentId).orElse(null);
		if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
			// Hoàn một khoản chưa thu hoặc không tồn tại là sai; cần người xem (overview.md §16.3).
			log.error("Không hoàn được payment {}: {}", paymentId,
					payment == null ? "không tồn tại" : "đang " + payment.getStatus());
			return;
		}
		Refund refund = refunds.findFirstByPaymentIdAndReasonOrderByCreatedAtDesc(paymentId, reason).orElse(null);
		if (refund != null && refund.getStatus() == RefundStatus.SUCCEEDED) {
			return;
		}
		if (refund == null) {
			// BR-07 / BR-08 sẽ tính số tiền khác; trường hợp này hoàn 100% những gì khách đã trả.
			refund = newTransaction.execute(status -> refunds
					.save(new Refund(Uuid7.next(clock), paymentId, payment.getAmountMinor(), reason, clock.instant())));
		}
		GatewayRefund result = stripe.createRefund(payment.getStripePaymentIntentId(), refund.getAmountMinor(),
				refund.getId(), "refund-" + refund.getId());

		Refund managed = refunds.findById(refund.getId()).orElseThrow();
		managed.recordStripeResult(result.id(), result.isSucceeded(), clock.instant());
		refunds.save(managed);
		log.warn("Đã hoàn {} cho payment {} ({}), Stripe: {}", managed.getAmountMinor(), paymentId, reason,
				result.status());
	}

}
