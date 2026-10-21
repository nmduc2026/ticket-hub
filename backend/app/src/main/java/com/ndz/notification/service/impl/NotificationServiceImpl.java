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

import com.ndz.notification.client.EmailSendException;
import com.ndz.notification.client.EmailSender;
import com.ndz.notification.config.NotificationProperties;
import com.ndz.notification.entity.NotificationLog;
import com.ndz.notification.entity.Template;
import com.ndz.notification.entity.Template.TemplateId;
import com.ndz.notification.entity.enums.Channel;
import com.ndz.notification.repository.NotificationLogRepository;
import com.ndz.notification.repository.NotificationProcessedEventRepository;
import com.ndz.notification.repository.TemplateRepository;
import com.ndz.notification.service.NotificationService;
import com.ndz.notification.util.TemplateRenderer;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.event.TicketIssued;

@Service
public class NotificationServiceImpl implements NotificationService {

	static final String CONSUMER = "notification.ticket-issued";
	static final String TICKET_ISSUED = "TICKET_ISSUED";

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
	private final Clock clock;

	NotificationServiceImpl(NotificationProcessedEventRepository processed, TemplateRepository templates,
			NotificationLogRepository logs, TemplateRenderer renderer, EmailSender emailSender,
			NotificationProperties properties, PlatformTransactionManager transactions, Clock clock) {
		this.processed = processed;
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
		try {
			Template template = templates.findById(new TemplateId(TICKET_ISSUED, Channel.EMAIL.name()))
					.orElseThrow(() -> new IllegalStateException("Thiếu mẫu " + TICKET_ISSUED + " kênh EMAIL"));
			Map<String, Object> variables = variables(event);
			emailSender.send(event.contactEmail(), renderer.renderSubject(template.getSubject(), variables),
					renderer.renderBody(template.getBodyTemplate(), variables));
		}
		catch (RuntimeException e) {
			recordFailure(event, e);
			throw e;
		}
		logs.save(NotificationLog.sent(Uuid7.next(clock), event.userId(), Channel.EMAIL, TICKET_ISSUED,
				event.messageId(), clock.instant()));
		log.info("Đã gửi email vé của booking {}", event.bookingId());
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
	private void recordFailure(TicketIssued event, RuntimeException cause) {
		String reason = cause instanceof EmailSendException ? cause.getMessage()
				: cause.getClass().getSimpleName() + ": " + cause.getMessage();
		UUID logId = Uuid7.next(clock);
		newTransaction.executeWithoutResult(status -> logs.save(NotificationLog.failed(logId, event.userId(),
				Channel.EMAIL, TICKET_ISSUED, event.messageId(), reason, clock.instant())));
		log.error("Gửi email vé của booking {} thất bại: {}", event.bookingId(), reason);
	}

}
