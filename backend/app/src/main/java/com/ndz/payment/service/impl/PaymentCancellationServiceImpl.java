package com.ndz.payment.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ndz.payment.client.GatewayPaymentIntent;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.client.StripeGatewayException;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.entity.enums.PaymentStatus;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.repository.PaymentProcessedEventRepository;
import com.ndz.payment.service.PaymentCancellationService;
import com.ndz.payment.service.PaymentSettlementService;

/**
 * Gọi Stripe trước khi khoá dòng payment: không giữ khoá DB trong lúc chờ dịch vụ ngoài. Lỗi hạ tầng (Stripe không
 * phản hồi) được ném lên để Modulith gửi lại event; đối soát (hạng mục 6) là lưới an toàn cuối.
 */
@Service
public class PaymentCancellationServiceImpl implements PaymentCancellationService {

	static final String CONSUMER = "payment.booking-closed";

	/** Mã lỗi của Stripe khi huỷ PaymentIntent không còn ở trạng thái huỷ được (đã succeeded hoặc đã canceled). */
	private static final String UNEXPECTED_STATE = "payment_intent_unexpected_state";

	private static final Logger log = LoggerFactory.getLogger(PaymentCancellationServiceImpl.class);

	private final PaymentRepository payments;
	private final PaymentProcessedEventRepository processed;
	private final StripeGateway stripe;
	private final PaymentSettlementService settlement;
	private final Clock clock;

	PaymentCancellationServiceImpl(PaymentRepository payments, PaymentProcessedEventRepository processed,
			StripeGateway stripe, PaymentSettlementService settlement, Clock clock) {
		this.payments = payments;
		this.processed = processed;
		this.stripe = stripe;
		this.settlement = settlement;
		this.clock = clock;
	}

	@Override
	public void onBookingClosed(UUID messageId, UUID bookingId) {
		if (!processed.markProcessed(CONSUMER, messageId)) {
			log.info("Bỏ qua event đóng booking đã xử lý {}", messageId);
			return;
		}
		Payment payment = payments.findByBookingId(bookingId).orElse(null);
		if (payment == null) {
			return; // chưa bắt đầu thanh toán nên chưa có PaymentIntent
		}
		if (payment.getStatus() != PaymentStatus.REQUIRES_PAYMENT && payment.getStatus() != PaymentStatus.PROCESSING) {
			return; // đã kết thúc (thành công hoặc đã huỷ)
		}
		GatewayPaymentIntent intent = cancelAtStripe(payment);

		// Khoá lại rồi mới đổi: webhook có thể đã chốt payment trong lúc ta gọi Stripe.
		Payment locked = payments.findByStripePaymentIntentId(payment.getStripePaymentIntentId()).orElseThrow();
		if (intent.isSucceeded()) {
			// Khách đã trả tiền trước khi huỷ kịp: xử lý như PaymentSucceeded (booking đã đóng sẽ do Booking quyết định).
			log.warn("PaymentIntent của booking {} đã succeeded, không huỷ được: xử lý như thanh toán thành công",
					bookingId);
			settlement.markSucceeded(locked, clock.instant());
		}
		else if (settlement.markCanceled(locked, clock.instant())) {
			log.info("Đã huỷ PaymentIntent của booking {}", bookingId);
		}
	}

	/** Huỷ PaymentIntent; Stripe từ chối vì sai trạng thái thì lấy lại PaymentIntent để biết đã succeeded hay canceled. */
	private GatewayPaymentIntent cancelAtStripe(Payment payment) {
		try {
			return stripe.cancelPaymentIntent(payment.getStripePaymentIntentId());
		}
		catch (StripeGatewayException e) {
			if (!UNEXPECTED_STATE.equals(e.code())) {
				throw e;
			}
			return stripe.retrievePaymentIntent(payment.getStripePaymentIntentId());
		}
	}

}
