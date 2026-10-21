package com.ndz.payment.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ndz.booking.api.BookingApi;
import com.ndz.booking.api.BookingApi.PaymentStart;
import com.ndz.payment.client.GatewayPaymentIntent;
import com.ndz.payment.client.StripeGateway;
import com.ndz.payment.client.StripeGatewayException;
import com.ndz.payment.dto.response.PaymentResponse;
import com.ndz.payment.entity.Payment;
import com.ndz.payment.repository.PaymentRepository;
import com.ndz.payment.service.PaymentService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.PlatformFee;
import com.ndz.shared.util.Uuid7;

/**
 * Không bọc cả use case trong transaction: Booking ghi mốc bắt đầu thanh toán trong transaction của nó, rồi mới gọi
 * Stripe (không gọi dịch vụ ngoài khi đang giữ khoá DB), và payment được ghi bằng một câu lệnh nguyên tử.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

	private final BookingApi bookings;
	private final StripeGateway stripe;
	private final PaymentRepository payments;
	private final Clock clock;

	PaymentServiceImpl(BookingApi bookings, StripeGateway stripe, PaymentRepository payments, Clock clock) {
		this.bookings = bookings;
		this.stripe = stripe;
		this.payments = payments;
		this.clock = clock;
	}

	@Override
	public PaymentResponse startPayment(UUID userId, UUID bookingId) {
		PaymentStart start = bookings.startPayment(userId, bookingId);
		long feeMinor = PlatformFee.of(start.amountMinor(), start.platformFeeBps());

		GatewayPaymentIntent intent = createIntent(start);
		Payment candidate = new Payment(Uuid7.next(clock), bookingId, intent.id(), start.amountMinor(), feeMinor,
				start.currency(), clock.instant());
		if (payments.insertIfAbsent(candidate)) {
			log.info("Tạo thanh toán {} cho booking {}", candidate.getId(), bookingId);
		}
		// Hai request cùng lúc: chỉ một bản ghi được thêm, cả hai trả bản ghi đó (cùng PaymentIntent nhờ idempotency key).
		Payment payment = payments.findByBookingId(bookingId).orElseThrow();
		return new PaymentResponse(payment.getId(), intent.clientSecret(), payment.getAmountMinor(),
				payment.getApplicationFeeMinor(), payment.getCurrency());
	}

	private GatewayPaymentIntent createIntent(PaymentStart start) {
		try {
			return stripe.createPaymentIntent(start.amountMinor(), start.currency(), start.bookingId(),
					"booking-" + start.bookingId());
		}
		catch (StripeGatewayException e) {
			log.warn("Không tạo được PaymentIntent cho booking {} (mã Stripe: {})", start.bookingId(), e.code());
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
					"Cổng thanh toán đang không phản hồi, vui lòng thử lại sau");
		}
	}

}
