package com.ndz.payment.service.impl;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.payment.client.GatewayRefund;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.client.StripeGatewayException;
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
	static final String REQUESTED_CONSUMER = "payment.refund-requested";

	private static final Logger log = LoggerFactory.getLogger(PaymentRefundServiceImpl.class);

	private final PaymentRepository payments;
	private final RefundRepository refunds;
	private final PaymentProcessedEventRepository processed;
	private final StripeGateway stripe;
	private final ApplicationEventPublisher publisher;
	private final TransactionTemplate newTransaction;
	private final Clock clock;

	PaymentRefundServiceImpl(PaymentRepository payments, RefundRepository refunds,
			PaymentProcessedEventRepository processed, StripeGateway stripe, ApplicationEventPublisher publisher,
			PlatformTransactionManager transactions, Clock clock) {
		this.payments = payments;
		this.refunds = refunds;
		this.processed = processed;
		this.stripe = stripe;
		this.publisher = publisher;
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
			refund = newRefund(() -> new Refund(Uuid7.next(clock), paymentId, payment.getAmountMinor(), reason,
					clock.instant()));
		}
		callStripe(payment, refund);
	}

	@Override
	public void refundForBooking(RefundRequested event) {
		if (!processed.markProcessed(REQUESTED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundRequested đã xử lý {}", event.messageId());
			return;
		}
		Payment payment = payments.findByBookingId(event.bookingId()).orElse(null);
		if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
			log.error("Booking {} yêu cầu hoàn nhưng payment {}: không hoàn được", event.bookingId(),
					payment == null ? "không tồn tại" : "đang " + payment.getStatus());
			publisher.publishEvent(new RefundFailed(Uuid7.next(clock), clock.instant(), null,
					payment == null ? null : payment.getId(), event.bookingId(), event.userId(),
					event.refundAmountMinor(), event.currency(), RefundFailed.PAYMENT_NOT_REFUNDABLE,
					event.eventTitle()));
			return;
		}
		long amount = refundAmount(payment, event.reason());
		if (amount != event.refundAmountMinor()) {
			log.warn("Booking {} báo hoàn {} nhưng payment {} tính ra {}: dùng số của Payment", event.bookingId(),
					event.refundAmountMinor(), payment.getId(), amount);
		}
		// Lần trước còn chờ Stripe (gọi lại sau lỗi tạm thời) thì dùng lại; ngược lại, kể cả sau FAILED, là lần thử mới.
		Refund refund = refunds.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(payment.getId(), RefundStatus.PENDING)
				.orElseGet(() -> newRefund(() -> new Refund(Uuid7.next(clock), payment.getId(), amount, event.reason(),
						event.bookingId(), event.userId(), event.contactEmail(), event.eventTitle(),
						clock.instant())));
		callStripe(payment, refund);
	}

	@Override
	public void onRefundSucceeded(UUID paymentId) {
		Optional<Refund> pending = refunds.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(paymentId,
				RefundStatus.PENDING);
		if (pending.isEmpty()) {
			log.warn("Webhook charge.refunded cho payment {} không có lần hoàn nào đang chờ: bỏ qua", paymentId);
			return;
		}
		complete(pending.get());
	}

	@Override
	public void onRefundFailed(UUID paymentId, String stripeRefundId, String failureCode) {
		Optional<Refund> found = stripeRefundId == null ? Optional.empty()
				: refunds.findByStripeRefundId(stripeRefundId);
		Refund refund = found.or(() -> refunds.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(paymentId,
				RefundStatus.PENDING)).orElse(null);
		if (refund == null) {
			log.warn("Webhook refund.failed cho payment {} không khớp lần hoàn nào: bỏ qua", paymentId);
			return;
		}
		fail(refund, failureCode == null ? "unknown" : failureCode);
	}

	/** BR-07: lý do khách yêu cầu hoàn trừ phí nền tảng đã chốt trên payment; các lý do khác hoàn 100% (BR-08). */
	private static long refundAmount(Payment payment, String reason) {
		if (RefundRequested.CUSTOMER_REQUEST.equals(reason)) {
			return payment.getAmountMinor() - payment.getApplicationFeeMinor();
		}
		return payment.getAmountMinor();
	}

	private Refund newRefund(Supplier<Refund> factory) {
		return newTransaction.execute(status -> refunds.save(factory.get()));
	}

	private void callStripe(Payment payment, Refund refund) {
		GatewayRefund result;
		try {
			result = stripe.createRefund(payment.getStripePaymentIntentId(), refund.getAmountMinor(), refund.getId(),
					"refund-" + refund.getId());
		}
		catch (StripeGatewayException e) {
			if (e.code() == null) {
				throw e; // mạng / quá thời gian: để event được thử lại, vẫn cùng bản ghi và key
			}
			log.error("Stripe từ chối hoàn {} của payment {}: {}", refund.getId(), payment.getId(), e.code());
			fail(refunds.findById(refund.getId()).orElseThrow(), e.code());
			return;
		}
		Refund managed = refunds.findById(refund.getId()).orElseThrow();
		managed.recordStripeResult(result.id(), false, clock.instant());
		managed = refunds.save(managed); // dùng bản trả về: ngoài transaction, bản cũ giữ version cũ
		log.warn("Đã gửi lệnh hoàn {} cho payment {} ({}), Stripe: {}", managed.getAmountMinor(), payment.getId(),
				managed.getReason(), result.status());
		if (result.isSucceeded()) {
			complete(managed);
		}
		else if (result.isFailed()) {
			fail(managed, "stripe_" + result.status());
		}
	}

	private void complete(Refund refund) {
		if (!refund.markSucceeded(clock.instant())) {
			log.info("Lần hoàn {} đã ở trạng thái {}: bỏ qua", refund.getId(), refund.getStatus());
			return;
		}
		refunds.save(refund);
		Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
		publisher.publishEvent(new RefundCompleted(Uuid7.next(clock), clock.instant(), refund.getId(),
				payment.getId(), bookingOf(refund, payment), refund.getUserId(), refund.getAmountMinor(),
				payment.getCurrency(), refund.getContactEmail(), refund.getEventTitle()));
		log.info("Hoàn tiền {} của payment {} đã xong", refund.getId(), payment.getId());
	}

	private void fail(Refund refund, String failureCode) {
		if (!refund.markFailed(failureCode, clock.instant())) {
			log.info("Lần hoàn {} đã ở trạng thái {}: bỏ qua", refund.getId(), refund.getStatus());
			return;
		}
		refunds.save(refund);
		Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
		publisher.publishEvent(new RefundFailed(Uuid7.next(clock), clock.instant(), refund.getId(), payment.getId(),
				bookingOf(refund, payment), refund.getUserId(), refund.getAmountMinor(), payment.getCurrency(),
				failureCode, refund.getEventTitle()));
		// Alert mức cao (overview.md §16.3): REFUND_FAILED cần Admin hoàn thủ công.
		log.error("Hoàn tiền {} của payment {} thất bại ({}): cần Admin xử lý", refund.getId(), payment.getId(),
				failureCode);
	}

	private static UUID bookingOf(Refund refund, Payment payment) {
		return refund.getBookingId() != null ? refund.getBookingId() : payment.getBookingId();
	}

}
