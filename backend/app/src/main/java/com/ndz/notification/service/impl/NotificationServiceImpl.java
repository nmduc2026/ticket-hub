package com.ndz.notification.service.impl;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.booking.event.BookingSalePaused;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundRequested;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.notification.client.EmailSendException;
import com.ndz.notification.client.EmailSender;
import com.ndz.notification.config.NotificationProperties;
import com.ndz.notification.entity.NotificationLog;
import com.ndz.notification.entity.Template;
import com.ndz.notification.entity.Template.TemplateId;
import com.ndz.notification.entity.enums.Channel;
import com.ndz.notification.entity.enums.NotificationType;
import com.ndz.notification.repository.NotificationLogRepository;
import com.ndz.notification.repository.NotificationPreferenceRepository;
import com.ndz.notification.repository.NotificationPreferenceRepository.Preference;
import com.ndz.notification.repository.NotificationProcessedEventRepository;
import com.ndz.notification.repository.TemplateRepository;
import com.ndz.notification.service.NotificationService;
import com.ndz.notification.util.MoneyFormat;
import com.ndz.notification.util.TemplateRenderer;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.event.TicketIssued;

@Service
public class NotificationServiceImpl implements NotificationService {

	static final String CONSUMER = "notification.ticket-issued";
	static final String TICKET_ISSUED = "TICKET_ISSUED";
	static final String REFUND_COMPLETED_CONSUMER = "notification.refund-completed";
	static final String REFUND_COMPLETED = "REFUND_COMPLETED";
	static final String ORGANIZER_APPROVED_CONSUMER = "notification.organizer-approved";
	static final String ORGANIZER_APPROVED = "ORGANIZER_APPROVED";
	static final String ORGANIZER_REJECTED_CONSUMER = "notification.organizer-rejected";
	static final String ORGANIZER_REJECTED = "ORGANIZER_REJECTED";
	static final String HOLD_EXPIRING_CONSUMER = "notification.hold-expiring";
	static final String SALE_PAUSED_CONSUMER = "notification.sale-paused";
	static final String EVENT_CANCELLED_CONSUMER = "notification.event-cancelled";

	/** BR-11: lưu UTC, chỉ đổi sang giờ Việt Nam ở lớp hiển thị. */
	private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

	private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

	private final NotificationProcessedEventRepository processed;
	private final TemplateRepository templates;
	private final NotificationLogRepository logs;
	private final TemplateRenderer renderer;
	private final EmailSender emailSender;
	private final NotificationProperties properties;
	private final TransactionTemplate newTransaction;
	private final NotificationPreferenceRepository preferences;
	private final Clock clock;

	NotificationServiceImpl(NotificationProcessedEventRepository processed, TemplateRepository templates,
			NotificationLogRepository logs, TemplateRenderer renderer, EmailSender emailSender,
			NotificationProperties properties, PlatformTransactionManager transactions,
			NotificationPreferenceRepository preferences, Clock clock) {
		this.processed = processed;
		this.preferences = preferences;
		this.templates = templates;
		this.logs = logs;
		this.renderer = renderer;
		this.emailSender = emailSender;
		this.properties = properties;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@Override
	public void onTicketIssued(TicketIssued event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua TicketIssued đã xử lý {}", event.messageId());
			return;
		}
		sendEmail(TICKET_ISSUED, event.userId(), event.messageId(), event.contactEmail(), variables(event),
				"vé của booking " + event.bookingId());
	}

	@Override
	public void onRefundCompleted(RefundCompleted event) {
		if (!processed.markProcessed(REFUND_COMPLETED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundCompleted đã xử lý {}", event.messageId());
			return;
		}
		if (event.userId() == null || event.contactEmail() == null) {
			// Hoàn tự động vì thanh toán đến muộn: không có người nhận để gửi thư.
			log.info("Hoàn tiền {} không có người nhận email, bỏ qua", event.refundId());
			return;
		}
		sendEmail(REFUND_COMPLETED, event.userId(), event.messageId(), event.contactEmail(),
				Map.of("eventTitle", event.eventTitle() == null ? "" : event.eventTitle(), "amount",
						MoneyFormat.format(event.amountMinor(), event.currency())),
				"hoàn tiền " + event.refundId());
	}

	@Override
	public void onHoldExpiring(BookingHoldExpiring event) {
		if (!processed.markProcessed(HOLD_EXPIRING_CONSUMER, event.messageId())) {
			log.info("Bỏ qua BookingHoldExpiring đã xử lý {}", event.messageId());
			return;
		}
		if (!emailEnabled(event.userId(), NotificationType.HOLD_EXPIRING)) {
			log.info("Người dùng {} đã tắt email {}, không gửi", event.userId(), NotificationType.HOLD_EXPIRING);
			return;
		}
		sendEmail(NotificationType.HOLD_EXPIRING.name(), event.userId(), event.messageId(), event.contactEmail(),
				Map.of("eventTitle", event.eventTitle(), "expiresAt", DISPLAY_TIME.format(event.expiresAt()),
						"checkoutUrl", properties.webBaseUrl() + "/checkout/" + event.bookingId()),
				"sắp hết giờ giữ ghế của booking " + event.bookingId());
	}

	@Override
	public void onSalePaused(BookingSalePaused event) {
		if (!processed.markProcessed(SALE_PAUSED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua BookingSalePaused đã xử lý {}", event.messageId());
			return;
		}
		if (!emailEnabled(event.userId(), NotificationType.SALE_PAUSED)) {
			log.info("Người dùng {} đã tắt email {}, không gửi", event.userId(), NotificationType.SALE_PAUSED);
			return;
		}
		sendEmail(NotificationType.SALE_PAUSED.name(), event.userId(), event.messageId(), event.contactEmail(),
				Map.of("eventTitle", event.eventTitle(), "confirmed", "CONFIRMED".equals(event.bookingStatus()),
						"bookingsUrl", properties.webBaseUrl() + "/me/bookings"),
				"tạm dừng bán của booking " + event.bookingId());
	}

	@Override
	public void onRefundRequested(RefundRequested event) {
		if (!RefundRequested.EVENT_CANCELLED.equals(event.reason()) || event.contactEmail() == null) {
			return;
		}
		if (!processed.markProcessed(EVENT_CANCELLED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua RefundRequested đã xử lý {}", event.messageId());
			return;
		}
		sendEmail(NotificationType.EVENT_CANCELLED.name(), event.userId(), event.messageId(), event.contactEmail(),
				Map.of("eventTitle", event.eventTitle(), "amount", MoneyFormat.format(event.refundAmountMinor(),
						event.currency()), "bookingsUrl", properties.webBaseUrl() + "/me/bookings"),
				"huỷ sự kiện của booking " + event.bookingId());
	}

	/** Loại bắt buộc luôn gửi; loại tuỳ chọn theo cờ {@code email} trong cài đặt (mặc định bật). */
	private boolean emailEnabled(UUID userId, NotificationType type) {
		return type.mandatory() || preferences.findAll(userId).getOrDefault(type.name(), Preference.DEFAULT).email();
	}

	@Override
	public void onOrganizerApproved(OrganizerApproved event) {
		if (!processed.markProcessed(ORGANIZER_APPROVED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua OrganizerApproved đã xử lý {}", event.messageId());
			return;
		}
		sendEmail(ORGANIZER_APPROVED, event.organizerId(), event.messageId(), event.contactEmail(),
				Map.of("displayName", event.displayName(), "organizerUrl", properties.webBaseUrl() + "/organizer"),
				"duyệt hồ sơ organizer " + event.organizerId());
	}

	@Override
	public void onOrganizerRejected(OrganizerRejected event) {
		if (!processed.markProcessed(ORGANIZER_REJECTED_CONSUMER, event.messageId())) {
			log.info("Bỏ qua OrganizerRejected đã xử lý {}", event.messageId());
			return;
		}
		sendEmail(ORGANIZER_REJECTED, event.organizerId(), event.messageId(), event.contactEmail(),
				Map.of("displayName", event.displayName(), "reason", event.reason(), "applyUrl",
						properties.webBaseUrl() + "/organizer/apply"),
				"từ chối hồ sơ organizer " + event.organizerId());
	}

	/**
	 * Dựng thư từ mẫu rồi gửi; ghi {@code notification_log} SENT, hoặc FAILED rồi ném lỗi để event được thử lại.
	 */
	private void sendEmail(String templateCode, UUID userId, UUID messageId, String to, Map<String, Object> variables,
			String description) {
		try {
			Template template = templates.findById(new TemplateId(templateCode, Channel.EMAIL.name()))
					.orElseThrow(() -> new IllegalStateException("Thiếu mẫu " + templateCode + " kênh EMAIL"));
			emailSender.send(to, renderer.renderSubject(template.getSubject(), variables),
					renderer.renderBody(template.getBodyTemplate(), variables));
		}
		catch (RuntimeException e) {
			recordFailure(templateCode, userId, messageId, description, e);
			throw e;
		}
		logs.save(NotificationLog.sent(Uuid7.next(clock), userId, Channel.EMAIL, templateCode, messageId,
				clock.instant()));
		log.info("Đã gửi email {}", description);
	}

	private Map<String, Object> variables(TicketIssued event) {
		List<Map<String, String>> tickets = event.tickets().stream()
				.map(ticket -> Map.of("seat",
						"Khu " + ticket.sectionCode() + " · Hàng " + ticket.rowLabel() + " · Ghế " + ticket.seatNumber(),
						"tier", ticket.tierName()))
				.toList();
		return Map.of("eventTitle", event.eventTitle(), "startsAt", DISPLAY_TIME.format(event.startsAt()),
				"venueName", event.venueName(), "tickets", tickets, "ticketsUrl",
				properties.webBaseUrl() + "/me/tickets");
	}

	/** Ghi trong transaction riêng: transaction chính sắp hoàn tác (và event được thử lại) nên dòng lỗi phải còn lại. */
	private void recordFailure(String templateCode, UUID userId, UUID messageId, String description,
			RuntimeException cause) {
		String reason = cause instanceof EmailSendException ? cause.getMessage()
				: cause.getClass().getSimpleName() + ": " + cause.getMessage();
		UUID logId = Uuid7.next(clock);
		newTransaction.executeWithoutResult(status -> logs.save(NotificationLog.failed(logId, userId, Channel.EMAIL,
				templateCode, messageId, reason, clock.instant())));
		log.error("Gửi email {} thất bại: {}", description, reason);
	}

}
