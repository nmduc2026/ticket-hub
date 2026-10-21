package com.ndz.payment.client;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

/**
 * Adapter gọi Stripe (overview.md §15.1). Domain chỉ thấy kiểu của gói này, không thấy SDK. Mỗi lệnh ghi có
 * idempotency key nên gọi lặp an toàn; mọi lời gọi đi qua {@link StripeResilience} (timeout 3 giây, retry chỉ khi có
 * idempotency key, circuit breaker, bulkhead).
 */
public class StripeGateway {

	private final StripeClient client;
	private final StripeResilience resilience;
	private final String webhookSecret;
	private final Duration webhookTolerance;
	private final Clock clock;

	public StripeGateway(StripeClient client, StripeResilience resilience, String webhookSecret,
			Duration webhookTolerance, Clock clock) {
		this.client = client;
		this.resilience = resilience;
		this.webhookSecret = webhookSecret;
		this.webhookTolerance = webhookTolerance;
		this.clock = clock;
	}

	/**
	 * Tạo PaymentIntent cho booking. Không gửi {@code application_fee_amount}: chưa có connected account (ADR 0022).
	 *
	 * @param idempotencyKey {@code booking-{bookingId}}: gọi lại cùng khoá trả đúng PaymentIntent đã tạo
	 */
	public GatewayPaymentIntent createPaymentIntent(long amountMinor, String currency, UUID bookingId,
			String idempotencyKey) {
		var params = PaymentIntentCreateParams.builder().setAmount(amountMinor).setCurrency(currency.toLowerCase())
				.setAutomaticPaymentMethods(
						PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
				.putAllMetadata(Map.of("bookingId", bookingId.toString())).build();
		var options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
		return call(true, "create PaymentIntent", () -> client.v1().paymentIntents().create(params, options));
	}

	public GatewayPaymentIntent retrievePaymentIntent(String paymentIntentId) {
		return call(false, "retrieve PaymentIntent", () -> client.v1().paymentIntents().retrieve(paymentIntentId));
	}

	/** Huỷ PaymentIntent; PaymentIntent đã {@code succeeded} thì Stripe từ chối và người gọi phải lấy lại trạng thái. */
	public GatewayPaymentIntent cancelPaymentIntent(String paymentIntentId) {
		return call(false, "cancel PaymentIntent", () -> client.v1().paymentIntents().cancel(paymentIntentId));
	}

	/**
	 * Hoàn tiền một phần hoặc toàn bộ cho PaymentIntent đã thanh toán.
	 *
	 * <p>Khi có Connect (destination charge, ADR 0022) hàm này chừa chỗ cho transfer reversal và
	 * {@code refund_application_fee}.
	 *
	 * @param idempotencyKey {@code refund-{refundId}}: gọi lại cùng khoá trả đúng lần hoàn đã tạo
	 */
	public GatewayRefund createRefund(String paymentIntentId, long amountMinor, UUID refundId,
			String idempotencyKey) {
		var params = RefundCreateParams.builder().setPaymentIntent(paymentIntentId).setAmount(amountMinor)
				.putMetadata("refundId", refundId.toString()).build();
		var options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
		return resilience.execute(true, () -> {
			try {
				Refund refund = client.v1().refunds().create(params, options);
				return new GatewayRefund(refund.getId(), refund.getStatus());
			}
			catch (StripeException e) {
				throw new StripeGatewayException("Stripe: create Refund thất bại", e.getCode(), e);
			}
		});
	}

	/**
	 * Xác thực chữ ký của webhook trên raw body và giờ ký (lệch quá dung sai thì từ chối, overview.md §13.4), rồi đọc
	 * những gì domain cần. Payload phải là đúng chuỗi byte Stripe đã ký.
	 *
	 * @throws InvalidWebhookException chữ ký sai, thiếu, lệch giờ, hoặc payload không đọc được
	 */
	public GatewayWebhookEvent parseWebhook(String payload, String signatureHeader) {
		if (webhookSecret == null || webhookSecret.isBlank()) {
			throw new InvalidWebhookException("Chưa cấu hình webhook secret", null);
		}
		if (signatureHeader == null || signatureHeader.isBlank()) {
			throw new InvalidWebhookException("Thiếu header Stripe-Signature", null);
		}
		Event event;
		try {
			event = Webhook.constructEvent(payload, signatureHeader, webhookSecret, webhookTolerance.toSeconds(),
					clock);
		}
		catch (SignatureVerificationException e) {
			throw new InvalidWebhookException("Chữ ký webhook không hợp lệ", e);
		}
		catch (RuntimeException e) {
			// Payload không phải JSON hợp lệ: SDK ném JsonSyntaxException (Gson không nằm trên classpath biên dịch).
			throw new InvalidWebhookException("Không đọc được nội dung webhook", e);
		}
		return toWebhookEvent(event);
	}

	private static GatewayWebhookEvent toWebhookEvent(Event event) {
		try {
			StripeObject object = event.getDataObjectDeserializer().deserializeUnsafe();
			if (object instanceof PaymentIntent intent) {
				String failureCode = intent.getLastPaymentError() == null ? null
						: intent.getLastPaymentError().getCode();
				return new GatewayWebhookEvent(event.getId(), event.getType(), intent.getId(), failureCode, null);
			}
			if (object instanceof Charge charge) {
				return new GatewayWebhookEvent(event.getId(), event.getType(), charge.getPaymentIntent(), null, null);
			}
			if (object instanceof Refund refund) {
				return new GatewayWebhookEvent(event.getId(), event.getType(), refund.getPaymentIntent(),
						refund.getFailureReason(), refund.getId());
			}
			return new GatewayWebhookEvent(event.getId(), event.getType(), null, null, null);
		}
		catch (EventDataObjectDeserializationException e) {
			throw new InvalidWebhookException("Không đọc được nội dung webhook", e);
		}
	}

	private GatewayPaymentIntent call(boolean idempotent, String action, StripeCall call) {
		return resilience.execute(idempotent, () -> {
			try {
				PaymentIntent intent = call.execute();
				return new GatewayPaymentIntent(intent.getId(), intent.getStatus(), intent.getClientSecret(),
						intent.getAmount(), intent.getCurrency().toUpperCase());
			}
			catch (StripeException e) {
				throw new StripeGatewayException("Stripe: " + action + " thất bại", e.getCode(), e);
			}
		});
	}

	@FunctionalInterface
	private interface StripeCall {

		PaymentIntent execute() throws StripeException;

	}

}
