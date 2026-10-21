package com.ndz.payment.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.api.CatalogApi.OrganizerContact;
import com.ndz.catalog.event.OrganizerPayoutStatusChanged;
import com.ndz.payment.client.InvalidWebhookException;
import com.ndz.payment.client.StripeConnectGateway;
import com.ndz.payment.client.StripeConnectGateway.AccountUpdate;
import com.ndz.payment.client.StripeConnectGateway.OnboardingLink;
import com.ndz.payment.client.StripeGatewayException;
import com.ndz.payment.config.StripeProperties;
import com.ndz.payment.dto.response.StripeAccountResponses.StripeAccountResponse;
import com.ndz.payment.dto.response.StripeAccountResponses.StripeLinkResponse;
import com.ndz.payment.repository.ConnectedAccountRepository;
import com.ndz.payment.repository.ConnectedAccountRepository.ConnectedAccount;
import com.ndz.payment.repository.StripeEventRepository;
import com.ndz.payment.service.OrganizerStripeService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
public class OrganizerStripeServiceImpl implements OrganizerStripeService {

	private static final Logger log = LoggerFactory.getLogger(OrganizerStripeServiceImpl.class);
	private static final String CURRENCY = "VND";

	private final ConnectedAccountRepository accounts;
	private final StripeEventRepository stripeEvents;
	private final StripeConnectGateway stripe;
	private final StripeProperties properties;
	private final CatalogApi catalog;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	OrganizerStripeServiceImpl(ConnectedAccountRepository accounts, StripeEventRepository stripeEvents,
			StripeConnectGateway stripe, StripeProperties properties, CatalogApi catalog,
			ApplicationEventPublisher publisher, Clock clock) {
		this.accounts = accounts;
		this.stripeEvents = stripeEvents;
		this.stripe = stripe;
		this.properties = properties;
		this.catalog = catalog;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public StripeLinkResponse onboardingLink(UUID organizerId) {
		OrganizerContact organizer = catalog.organizerContact(organizerId).filter(found -> "APPROVED".equals(found.status()))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZER_NOT_APPROVED,
						"Hồ sơ ban tổ chức chưa được duyệt"));
		String accountId = accounts.findByOrganizer(organizerId).map(ConnectedAccount::stripeAccountId)
				.orElseGet(() -> createAccount(organizerId, organizer.contactEmail()));
		OnboardingLink link = providerCall(() -> stripe.createOnboardingLink(accountId, properties.connectRefreshUrl(),
				properties.connectReturnUrl()));
		return new StripeLinkResponse(link.url(), link.expiresAt());
	}

	@Override
	public StripeAccountResponse account(UUID organizerId) {
		return accounts.findByOrganizer(organizerId).map(this::toResponse)
				.orElseGet(() -> new StripeAccountResponse("none", false, false, List.of(), null, CURRENCY));
	}

	@Override
	public StripeLinkResponse loginLink(UUID organizerId) {
		ConnectedAccount account = accounts.findByOrganizer(organizerId).orElseThrow(
				() -> new BusinessException(ErrorCode.STRIPE_NOT_CONNECTED, "Chưa kết nối Stripe"));
		return new StripeLinkResponse(providerCall(() -> stripe.createLoginLink(account.stripeAccountId())), null);
	}

	@Override
	@Transactional
	public void handleConnectWebhook(String payload, String signature) {
		AccountUpdate update;
		try {
			update = stripe.parseWebhook(payload, signature);
		}
		catch (InvalidWebhookException e) {
			log.warn("Từ chối webhook Connect: {}", e.getMessage());
			throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook không hợp lệ");
		}
		if (!stripeEvents.insertIfAbsent(update.eventId(), update.type(), payload, clock.instant())) {
			log.info("Bỏ qua webhook Connect đã nhận {}", update.eventId());
			return;
		}
		if (!"account.updated".equals(update.type()) || update.accountId() == null) {
			log.info("Webhook Connect {} loại {} không cần xử lý", update.eventId(), update.type());
			return;
		}
		accounts.applyUpdate(update.accountId(), update.chargesEnabled(), update.payoutsEnabled(),
				update.detailsSubmitted(), update.requirementsDue(), update.disabledReason(), clock.instant())
				.ifPresentOrElse(change -> {
					if (change.chargesEnabledBefore() != update.chargesEnabled()) {
						publisher.publishEvent(new OrganizerPayoutStatusChanged(Uuid7.next(clock), clock.instant(),
								change.organizerId(), update.chargesEnabled()));
						log.info("Organizer {}: charges_enabled → {}", change.organizerId(), update.chargesEnabled());
					}
				}, () -> log.warn("account.updated cho account lạ {}", update.accountId()));
	}

	/** Gọi Stripe ngoài transaction; idempotency key cố định theo organizer nên tạo lặp vẫn ra cùng account. */
	private String createAccount(UUID organizerId, String email) {
		String accountId = providerCall(
				() -> stripe.createExpressAccount(organizerId, email, "connect-account-" + organizerId));
		accounts.insertIfAbsent(organizerId, accountId, clock.instant());
		log.info("Tạo connected account {} cho organizer {}", accountId, organizerId);
		return accountId;
	}

	/** Suy {@code state} theo thứ tự ở overview.md §7.6. */
	private StripeAccountResponse toResponse(ConnectedAccount account) {
		String state;
		if (account.chargesEnabled()) {
			state = "active";
		}
		else if (!account.detailsSubmitted() || !account.requirementsDue().isEmpty()) {
			state = "incomplete";
		}
		else if (account.disabledReason() == null || "requirements.pending_verification".equals(account.disabledReason())) {
			state = "pending";
		}
		else {
			state = "restricted";
		}
		return new StripeAccountResponse(state, account.chargesEnabled(), account.payoutsEnabled(),
				account.requirementsDue(), balance(account), CURRENCY);
	}

	/** Số dư chỉ để hiển thị: Stripe lỗi thì bỏ trống, không làm hỏng cả màn. */
	private Long balance(ConnectedAccount account) {
		if (!account.chargesEnabled()) {
			return null;
		}
		try {
			return stripe.availableBalance(account.stripeAccountId(), CURRENCY);
		}
		catch (StripeGatewayException e) {
			log.warn("Không đọc được số dư của {}: {}", account.stripeAccountId(), e.getMessage());
			return null;
		}
	}

	private static <T> T providerCall(java.util.function.Supplier<T> call) {
		try {
			return call.get();
		}
		catch (StripeGatewayException e) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
					"Cổng thanh toán đang không phản hồi, vui lòng thử lại sau");
		}
	}

}
