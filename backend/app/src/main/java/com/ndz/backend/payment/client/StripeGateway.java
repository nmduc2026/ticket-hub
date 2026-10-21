package com.ndz.backend.payment.client;

import java.util.Map;
import java.util.UUID;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * Adapter gọi Stripe (overview.md §15.1). Domain chỉ thấy kiểu của gói này, không thấy SDK. Mỗi lệnh ghi có
 * idempotency key nên gọi lặp an toàn; retry / circuit breaker thêm ở Sprint 4 (Resilience4j).
 */
public class StripeGateway {

	private final StripeClient client;

	public StripeGateway(StripeClient client) {
		this.client = client;
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
		return call("create PaymentIntent", () -> client.v1().paymentIntents().create(params, options));
	}

	public GatewayPaymentIntent retrievePaymentIntent(String paymentIntentId) {
		return call("retrieve PaymentIntent", () -> client.v1().paymentIntents().retrieve(paymentIntentId));
	}

	/** Huỷ PaymentIntent; PaymentIntent đã {@code succeeded} thì Stripe từ chối và người gọi phải lấy lại trạng thái. */
	public GatewayPaymentIntent cancelPaymentIntent(String paymentIntentId) {
		return call("cancel PaymentIntent", () -> client.v1().paymentIntents().cancel(paymentIntentId));
	}

	private static GatewayPaymentIntent call(String action, StripeCall call) {
		try {
			PaymentIntent intent = call.execute();
			return new GatewayPaymentIntent(intent.getId(), intent.getStatus(), intent.getClientSecret(),
					intent.getAmount(), intent.getCurrency().toUpperCase());
		}
		catch (StripeException e) {
			throw new StripeGatewayException("Stripe: " + action + " thất bại", e.getCode(), e);
		}
	}

	@FunctionalInterface
	private interface StripeCall {

		PaymentIntent execute() throws StripeException;

	}

}
