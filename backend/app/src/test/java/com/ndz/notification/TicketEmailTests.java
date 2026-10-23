package com.ndz.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MailpitApi;
import com.ndz.notification.service.NotificationService;
import com.ndz.ticket.event.TicketIssued;

/**
 * Email vé (plan Sprint 3 việc 8; AC-2; overview.md §6.5, §11.2): {@code TicketIssued} thì gửi email tới
 * {@code contact_email}, nội dung dựng từ mẫu Thymeleaf trong DB, idempotent theo event. Mailpit thật bằng Testcontainers.
 */
@IntegrationTest
class TicketEmailTests {

	private static final Instant STARTS_AT = Instant.parse("2026-12-01T12:30:00Z"); // 19:30 giờ Việt Nam

	@Autowired
	MailpitApi mailpit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	NotificationService notifications;

	@BeforeEach
	void prepare() {
		cleanUp();
		mailpit.clear();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notification.notification_log");
		jdbc.update("delete from notification.processed_events");
	}

	@Test
	@DisplayName("AC-2: TicketIssued thì email vé tới contact_email, có tên sự kiện, giờ Việt Nam, địa điểm, ghế và link Vé của tôi")
	void ticketIssuedSendsTheTicketEmail() {
		String to = unique();
		TicketIssued event = issued(to, "Concert <Mùa hè>");

		publish(event);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mailpit.countTo(to)).isEqualTo(1));
		assertThat(mailpit.subjectsTo(to)).containsExactly("Vé của bạn cho Concert <Mùa hè>");
		String html = mailpit.latestHtmlTo(to);
		assertThat(html).contains("Concert &lt;Mùa hè&gt;") // Thymeleaf escape giá trị chèn vào HTML
				.contains("01/12/2026 19:30").contains("Sài Gòn Arena").contains("Khu A · Hàng B · Ghế 5")
				.contains("Khu A · Hàng B · Ghế 6").contains("VIP").contains("http://localhost:5173/me/tickets");
		assertThat(jdbc.queryForMap("select status, template_code, channel, user_id from notification.notification_log"))
				.containsEntry("status", "SENT").containsEntry("template_code", "TICKET_ISSUED")
				.containsEntry("channel", "EMAIL").containsEntry("user_id", event.userId());
	}

	@Test
	@DisplayName("§8.3: nhận TicketIssued hai lần thì chỉ một email")
	void theSameEventTwiceSendsOneEmail() {
		String to = unique();
		TicketIssued event = issued(to, "Concert");

		publish(event);
		publish(event);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mailpit.countTo(to)).isEqualTo(1));
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
			assertThat(mailpit.countTo(to)).isEqualTo(1);
			assertThat(count("select count(*) from notification.notification_log where status = 'SENT' and user_id = ?",
					event.userId())).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("Thiếu mẫu: không gửi, ghi log FAILED (không kèm email người nhận), không đánh dấu đã xử lý để thử lại")
	void aMissingTemplateIsLoggedAsFailedAndLeavesTheEventRetryable() {
		String body = jdbc.queryForObject("select body_template from notification.templates where code = 'TICKET_ISSUED'",
				String.class);
		String subject = jdbc.queryForObject("select subject from notification.templates where code = 'TICKET_ISSUED'",
				String.class);
		jdbc.update("delete from notification.templates where code = 'TICKET_ISSUED'");
		try {
			String to = unique();
			TicketIssued event = issued(to, "Concert");

			assertThatThrownBy(() -> transaction.executeWithoutResult(status -> notifications.onTicketIssued(event)))
					.isInstanceOf(IllegalStateException.class);

			assertThat(mailpit.countTo(to)).isZero();
			assertThat(jdbc.queryForMap("select status, error from notification.notification_log"))
					.containsEntry("status", "FAILED");
			assertThat(jdbc.queryForObject("select error from notification.notification_log", String.class))
					.doesNotContain(to);
			assertThat(count("select count(*) from notification.processed_events")).isZero();
		}
		finally {
			jdbc.update("insert into notification.templates (code, channel, subject, body_template) "
					+ "values ('TICKET_ISSUED', 'EMAIL', ?, ?)", subject, body);
		}
	}

	private void publish(TicketIssued event) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(event));
	}

	private static String unique() {
		return "khach-" + UUID.randomUUID() + "@example.com";
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private static TicketIssued issued(String email, String title) {
		return new TicketIssued(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), email, title, STARTS_AT, "Sài Gòn Arena",
				List.of(new TicketIssued.Ticket(UUID.randomUUID(), "A", "B", 5, "VIP"),
						new TicketIssued.Ticket(UUID.randomUUID(), "A", "B", 6, "VIP")));
	}

}
