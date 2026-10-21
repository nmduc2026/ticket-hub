package com.ndz.payment.service;

import java.util.UUID;

import com.ndz.payment.dto.response.StripeAccountResponses.StripeAccountResponse;
import com.ndz.payment.dto.response.StripeAccountResponses.StripeLinkResponse;

/** Kết nối Stripe Connect Express của organizer (overview.md §7.6, BR-05, ADR 0022). */
public interface OrganizerStripeService {

	/** Tạo Express account nếu chưa có rồi trả link onboarding; organizer chưa được duyệt → ORGANIZER_NOT_APPROVED. */
	StripeLinkResponse onboardingLink(UUID organizerId);

	/** Trạng thái suy từ {@code connected_accounts} (không gọi Stripe), riêng số dư đọc Balance API. */
	StripeAccountResponse account(UUID organizerId);

	/** Link vào Express Dashboard; chưa kết nối → STRIPE_NOT_CONNECTED. */
	StripeLinkResponse loginLink(UUID organizerId);

	/**
	 * Webhook endpoint Connect: chữ ký bằng secret riêng, khử trùng theo {@code stripe_event_id};
	 * {@code account.updated} cập nhật {@code connected_accounts}, {@code charges_enabled} đổi giá trị thì phát
	 * {@code OrganizerPayoutStatusChanged}.
	 */
	void handleConnectWebhook(String payload, String signature);

}
