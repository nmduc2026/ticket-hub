package com.ndz.payment.service;

import com.ndz.payment.dto.response.AdminStripeConfigResponses.DeliveryFailurePage;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.PaymentConfig;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.ReprocessResult;
import com.ndz.payment.dto.response.AdminStripeConfigResponses.WebhookCheckResult;

/** Cấu hình thanh toán chỉ đọc cho Admin (màn #36, overview.md §11.2, §13.4). */
public interface AdminStripeConfigService {

	PaymentConfig config();

	DeliveryFailurePage failedDeliveries();

	ReprocessResult reprocess(String stripeEventId);

	/** Tự gửi payload thử có chữ ký tới {@code POST /api/v1/webhooks/stripe}, trả mã phản hồi thật. */
	WebhookCheckResult webhookCheck();

}
