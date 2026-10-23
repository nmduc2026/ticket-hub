package com.ndz.payment.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;

/**
 * Adapter Stripe Connect (Express, ADR 0022, overview.md §7.6): tạo connected account, link onboarding, link vào Express
 * Dashboard, số dư, và đọc webhook {@code account.updated} của endpoint Connect (signing secret riêng). Mọi lời gọi đi
 * qua {@link StripeResilience}; lệnh tạo có idempotency key nên gọi lặp an toàn.
 */
public class StripeConnectGateway {

	private final StripeClient client;
	private final StripeResilience resilience;
	private final String connectWebhookSecret;
	private final Duration webhookTolerance;
	private final Clock clock;

	public StripeConnectGateway(StripeClient client, StripeResilience resilience, String connectWebhookSecret,
			Duration webhookTolerance, Clock clock) {
		this.client = client;
		this.resilience = resilience;
		this.connectWebhookSecret = connectWebhookSecret;
		this.webhookTolerance = webhookTolerance;
		this.clock = clock;
	}

	/**
	 * Tạo Express account cho organizer.
	 *
	 * @param idempotencyKey {@code connect-account-{organizerId}}: gọi lại trả đúng account đã tạo
	 */
	public String createExpressAccount(UUID organizerId, String email, String idempotencyKey) {
		var params = AccountCreateParams.builder().setType(AccountCreateParams.Type.EXPRESS).setCountry("SG")
				.setEmail(email).putMetadata("organizerId", organizerId.toString()).build();
		var options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
		return call(true, "create Account", () -> client.v1().accounts().create(params, options).getId());
	}

	/** Link onboarding một lần (hết hạn sau vài phút); hết hạn thì tạo link mới. */
	public OnboardingLink createOnboardingLink(String accountId, String refreshUrl, String returnUrl) {
		var params = AccountLinkCreateParams.builder().setAccount(accountId).setRefreshUrl(refreshUrl)
				.setReturnUrl(returnUrl).setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING).build();
		return call(false, "create AccountLink", () -> {
			var link = client.v1().accountLinks().create(params);
			return new OnboardingLink(link.getUrl(), Instant.ofEpochSecond(link.getExpiresAt()));
		});
	}

	/** Link đăng nhập Express Dashboard của organizer. */
	public String createLoginLink(String accountId) {
		return call(false, "create LoginLink", () -> client.v1().accounts().loginLinks().create(accountId).getUrl());
	}

	/** Số dư khả dụng của connected account theo {@code currency}; không có thì 0. */
	public long availableBalance(String accountId, String currency) {
		var options = RequestOptions.builder().setStripeAccount(accountId).build();
		return call(false, "retrieve Balance", () -> client.v1().balance().retrieve(options).getAvailable().stream()
				.filter(amount -> currency.equalsIgnoreCase(amount.getCurrency())).mapToLong(amount -> amount.getAmount())
				.sum());
	}

	/**
	 * Xác thực webhook của endpoint Connect bằng secret riêng (secret của endpoint platform không qua được) và đọc
	 * {@code account.updated}.
	 *
	 * @throws InvalidWebhookException chữ ký sai / thiếu / lệch giờ, hoặc payload không đọc được
	 */
	public AccountUpdate parseWebhook(String payload, String signatureHeader) {
		if (connectWebhookSecret == null || connectWebhookSecret.isBlank()) {
			throw new InvalidWebhookException("Chưa cấu hình webhook secret của Connect", null);
		}
		if (signatureHeader == null || signatureHeader.isBlank()) {
			throw new InvalidWebhookException("Thiếu header Stripe-Signature", null);
		}
		Event event;
		try {
			event = Webhook.constructEvent(payload, signatureHeader, connectWebhookSecret, webhookTolerance.toSeconds(),
					clock);
		}
		catch (SignatureVerificationException e) {
			throw new InvalidWebhookException("Chữ ký webhook không hợp lệ", e);
		}
		catch (RuntimeException e) {
			throw new InvalidWebhookException("Không đọc được nội dung webhook", e);
		}
		try {
			StripeObject object = event.getDataObjectDeserializer().deserializeUnsafe();
			if (!(object instanceof Account account)) {
				return new AccountUpdate(event.getId(), event.getType(), null, false, false, false, List.of(), null);
			}
			var requirements = account.getRequirements();
			return new AccountUpdate(event.getId(), event.getType(), account.getId(),
					Boolean.TRUE.equals(account.getChargesEnabled()), Boolean.TRUE.equals(account.getPayoutsEnabled()),
					Boolean.TRUE.equals(account.getDetailsSubmitted()),
					requirements == null || requirements.getCurrentlyDue() == null ? List.of()
							: requirements.getCurrentlyDue(),
					requirements == null ? null : requirements.getDisabledReason());
		}
		catch (EventDataObjectDeserializationException e) {
			throw new InvalidWebhookException("Không đọc được nội dung webhook", e);
		}
	}

	private <T> T call(boolean idempotent, String action, ConnectCall<T> call) {
		return resilience.execute(idempotent, () -> {
			try {
				return call.execute();
			}
			catch (StripeException e) {
				throw new StripeGatewayException("Stripe: " + action + " thất bại", e.getCode(), e);
			}
		});
	}

	@FunctionalInterface
	private interface ConnectCall<T> {

		T execute() throws StripeException;

	}

	public record OnboardingLink(String url, Instant expiresAt) {
	}

	/** Nội dung domain cần của {@code account.updated}; {@code accountId} null khi event không phải về account. */
	public record AccountUpdate(String eventId, String type, String accountId, boolean chargesEnabled,
			boolean payoutsEnabled, boolean detailsSubmitted, List<String> requirementsDue, String disabledReason) {
	}

}
