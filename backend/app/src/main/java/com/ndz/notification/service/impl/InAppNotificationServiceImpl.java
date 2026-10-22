package com.ndz.notification.service.impl;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.booking.event.BookingSalePaused;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.account.api.AccountApi;
import com.ndz.booking.event.RefundRequested;
import com.ndz.catalog.event.EventSuspended;
import com.ndz.catalog.event.OrganizerApplied;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.payment.event.StripeAccountRestricted;
import com.ndz.notification.config.NotificationProperties;
import com.ndz.notification.dto.request.UpdatePreferencesRequest;
import com.ndz.notification.dto.response.NotificationResponses.NotificationItem;
import com.ndz.notification.dto.response.NotificationResponses.NotificationPage;
import com.ndz.notification.dto.response.NotificationResponses.PreferenceItem;
import com.ndz.notification.entity.enums.NotificationType;
import com.ndz.notification.repository.InAppNotificationRepository;
import com.ndz.notification.repository.InAppNotificationRepository.Row;
import com.ndz.notification.repository.NotificationPreferenceRepository;
import com.ndz.notification.repository.NotificationPreferenceRepository.Preference;
import com.ndz.notification.service.InAppNotificationService;
import com.ndz.notification.util.MoneyFormat;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.event.TicketIssued;

@Service
public class InAppNotificationServiceImpl implements InAppNotificationService {

	private static final int MAX_PAGE_SIZE = 50;

	private static final Logger log = LoggerFactory.getLogger(InAppNotificationServiceImpl.class);

	private final InAppNotificationRepository inbox;
	private final NotificationPreferenceRepository preferences;
	private final NotificationProperties properties;
	private final AccountApi accounts;
	private final Clock clock;

	InAppNotificationServiceImpl(InAppNotificationRepository inbox, NotificationPreferenceRepository preferences,
			NotificationProperties properties, AccountApi accounts, Clock clock) {
		this.inbox = inbox;
		this.preferences = preferences;
		this.properties = properties;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Override
	@Transactional
	public boolean notify(UUID userId, NotificationType type, String title, String body, String link,
			UUID sourceEventId) {
		if (!type.mandatory() && !preferences.findAll(userId).getOrDefault(type.name(), Preference.DEFAULT).inApp()) {
			log.debug("Người dùng {} đã tắt thông báo {}", userId, type);
			return false;
		}
		boolean created = inbox.insertIfAbsent(Uuid7.next(clock), userId, type.name(), title, body, link,
				sourceEventId, clock.instant());
		if (!created) {
			log.info("Bỏ qua thông báo {} đã có cho event {}", type, sourceEventId);
		}
		return created;
	}

	@Override
	public void onTicketIssued(TicketIssued event) {
		int count = event.tickets().size();
		notify(event.userId(), NotificationType.TICKET_ISSUED, "Vé của bạn đã sẵn sàng",
				event.eventTitle() + ": " + count + " vé. Mở mã QR để vào cổng.", "/me/tickets", event.messageId());
	}

	@Override
	@Transactional
	public void onRefundRequested(RefundRequested event) {
		if (RefundRequested.EVENT_CANCELLED.equals(event.reason())) {
			// BR-08: thông báo bắt buộc, không tắt được.
			notify(event.userId(), NotificationType.EVENT_CANCELLED, "Sự kiện bị huỷ",
					event.eventTitle() + ": sự kiện đã bị huỷ, vé bị thu hồi và bạn được hoàn toàn bộ "
							+ MoneyFormat.format(event.refundAmountMinor(), event.currency()) + ".",
					"/me/bookings", event.messageId());
			return;
		}
		notify(event.userId(), NotificationType.REFUND_REQUESTED, "Đã nhận yêu cầu hoàn vé",
				event.eventTitle() + ": vé đã bị thu hồi, bạn sẽ nhận lại "
						+ MoneyFormat.format(event.refundAmountMinor(), event.currency()) + ".",
				"/me/bookings", event.messageId());
		if (event.organizerId() != null) {
			// Organizer của sự kiện (overview.md §6.5); chỉ có khi khách vừa gửi yêu cầu, hoàn thủ công không báo lại.
			notify(event.organizerId(), NotificationType.CUSTOMER_REFUND_REQUESTED, "Khách yêu cầu hoàn vé",
					event.eventTitle() + ": một đơn vừa được yêu cầu hoàn, vé đã thu hồi và ghế sẽ mở bán lại.",
					"/organizer/orders", event.messageId());
		}
	}

	@Override
	public void onHoldExpiring(BookingHoldExpiring event) {
		notify(event.userId(), NotificationType.HOLD_EXPIRING, "Sắp hết giờ giữ ghế",
				event.eventTitle() + ": ghế sẽ được nhả nếu chưa thanh toán trong vài phút tới.",
				"/checkout/" + event.bookingId(), event.messageId());
	}

	@Override
	public void onSalePaused(BookingSalePaused event) {
		String body = "CONFIRMED".equals(event.bookingStatus())
				? event.eventTitle() + ": sự kiện tạm dừng bán. Vé của bạn vẫn giữ nguyên."
				: event.eventTitle() + ": sự kiện tạm dừng bán, đơn đang giữ ghế có thể không thanh toán được.";
		notify(event.userId(), NotificationType.SALE_PAUSED, "Sự kiện tạm dừng bán", body,
				"/me/bookings", event.messageId());
	}

	@Override
	@Transactional
	public void onEventSuspended(EventSuspended event) {
		notify(event.organizerId(), NotificationType.SALE_PAUSED, "Sự kiện bị tạm dừng bán",
				event.eventTitle() + ": " + event.reason(), "/organizer/events/" + event.eventId() + "/edit",
				event.messageId());
		if (!event.automatic()) {
			return; // Admin tự bấm tạm dừng: không cần báo lại Admin
		}
		for (AccountApi.UserContact admin : accounts.usersWithRole("ADMIN")) {
			notify(admin.userId(), NotificationType.EVENT_AUTO_SUSPENDED, "Sự kiện tự tạm dừng",
					event.eventTitle() + ": " + event.reason(), "/admin/events", event.messageId());
		}
	}

	@Override
	@Transactional
	public void onStripeAccountRestricted(StripeAccountRestricted event) {
		for (AccountApi.UserContact admin : accounts.usersWithRole("ADMIN")) {
			notify(admin.userId(), NotificationType.STRIPE_RESTRICTED, "Stripe của organizer bị hạn chế",
					event.organizerName() + ": tài khoản Stripe bị hạn chế (" + event.disabledReason() + ").", "/admin/payments",
					event.messageId());
		}
	}

	@Override
	public void onRefundCompleted(RefundCompleted event) {
		if (event.userId() == null) {
			return; // hoàn tự động vì thanh toán đến muộn: không có người nhận
		}
		notify(event.userId(), NotificationType.REFUND_COMPLETED, "Đã hoàn tiền",
				(event.eventTitle() == null ? "Đơn của bạn" : event.eventTitle()) + ": đã hoàn "
						+ MoneyFormat.format(event.amountMinor(), event.currency()) + " về phương thức thanh toán ban đầu.",
				"/me/bookings", event.messageId());
	}

	@Override
	public void onRefundFailed(RefundFailed event) {
		if (event.userId() == null) {
			return;
		}
		notify(event.userId(), NotificationType.REFUND_FAILED, "Hoàn tiền đang được xử lý",
				(event.eventTitle() == null ? "Đơn của bạn" : event.eventTitle())
						+ ": hoàn tiền chưa thành công, chúng tôi đang xử lý thủ công và sẽ báo khi xong.",
				"/me/bookings", event.messageId());
	}

	@Override
	@Transactional
	public void onOrganizerApplied(OrganizerApplied event) {
		// Mỗi Admin một thông báo; (user, source_event_id) duy nhất nên gửi lại event không tạo trùng.
		for (AccountApi.UserContact admin : accounts.usersWithRole("ADMIN")) {
			notify(admin.userId(), NotificationType.ORGANIZER_APPLICATION_PENDING, "Hồ sơ organizer chờ duyệt",
					event.displayName() + " vừa nộp hồ sơ làm ban tổ chức.", "/admin/organizers", event.messageId());
		}
	}

	@Override
	@Transactional
	public void onOrganizerApproved(OrganizerApproved event) {
		notify(event.organizerId(), NotificationType.ORGANIZER_APPROVED, "Hồ sơ organizer đã được duyệt",
				"Bạn đã là ban tổ chức. Đăng nhập lại để thấy menu Ban tổ chức.", "/organizer", event.messageId());
	}

	@Override
	@Transactional
	public void onOrganizerRejected(OrganizerRejected event) {
		notify(event.organizerId(), NotificationType.ORGANIZER_REJECTED, "Hồ sơ organizer chưa được duyệt",
				event.reason(), "/organizer/apply", event.messageId());
	}

	@Override
	@Transactional(readOnly = true)
	public NotificationPage list(UUID userId, boolean unreadOnly, UUID cursor, int size) {
		int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		// Lấy dư một bản để biết còn trang sau hay không.
		List<Row> rows = inbox.list(userId, unreadOnly, cursor, limit + 1);
		boolean hasMore = rows.size() > limit;
		List<Row> page = hasMore ? rows.subList(0, limit) : rows;
		List<NotificationItem> items = page.stream().map(row -> new NotificationItem(row.id(), row.type(),
				row.title(), row.body(), row.link(), row.createdAt(), row.readAt())).toList();
		return new NotificationPage(items, hasMore ? page.get(page.size() - 1).id() : null);
	}

	@Override
	@Transactional(readOnly = true)
	public int unreadCount(UUID userId) {
		return inbox.unreadCount(userId);
	}

	@Override
	@Transactional
	public void markRead(UUID userId, UUID notificationId) {
		if (!inbox.markRead(userId, notificationId, clock.instant())) {
			throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND, "Không tìm thấy thông báo");
		}
	}

	@Override
	@Transactional
	public int markAllRead(UUID userId) {
		return inbox.markAllRead(userId, clock.instant());
	}

	@Override
	@Transactional(readOnly = true)
	public List<PreferenceItem> preferences(UUID userId) {
		Map<String, Preference> saved = preferences.findAll(userId);
		return Arrays.stream(NotificationType.values()).map(type -> {
			Preference preference = type.mandatory() ? Preference.DEFAULT
					: saved.getOrDefault(type.name(), Preference.DEFAULT);
			return new PreferenceItem(type.name(), type.label(), type.mandatory(), preference.email(),
					preference.inApp());
		}).toList();
	}

	@Override
	@Transactional
	public List<PreferenceItem> updatePreferences(UUID userId, UpdatePreferencesRequest request) {
		// Kiểm hết trước khi lưu: một lựa chọn sai thì không lưu lựa chọn nào.
		for (UpdatePreferencesRequest.Change change : request.preferences()) {
			NotificationType type = parse(change.type());
			if (type.mandatory() && (!change.email() || !change.inApp())) {
				throw new BusinessException(ErrorCode.NOTIFICATION_MANDATORY,
						"Thông báo về vé, hoàn tiền và huỷ sự kiện không tắt được");
			}
		}
		for (UpdatePreferencesRequest.Change change : request.preferences()) {
			NotificationType type = parse(change.type());
			if (!type.mandatory()) {
				preferences.upsert(userId, type.name(), change.email(), change.inApp());
			}
		}
		return preferences(userId);
	}

	@Override
	@Transactional
	public int purgeExpired() {
		int deleted = inbox.deleteCreatedBefore(clock.instant().minus(properties.inboxRetention()));
		if (deleted > 0) {
			log.info("Đã xoá {} thông báo cũ hơn {}", deleted, properties.inboxRetention());
		}
		return deleted;
	}

	private static NotificationType parse(String code) {
		try {
			return NotificationType.valueOf(code);
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.UNKNOWN_NOTIFICATION_TYPE, "Loại thông báo không có: " + code);
		}
	}

}
