package com.ndz.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;
import com.ndz.notification.entity.enums.NotificationType;
import com.ndz.notification.service.InAppNotificationService;

/**
 * Luồng SSE thông báo (plan Sprint 7 hạng mục 4, AC-7; overview.md §12): chuông nhận thông báo mới và số chưa đọc qua
 * SSE, mỗi user chỉ nhận thông báo của mình.
 */
@IntegrationTest
class NotificationStreamTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	InAppNotificationService notifications;

	@Autowired
	TransactionTemplate transactions;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notification.notifications");
	}

	@Test
	@DisplayName("AC-7: nối luồng nhận ngay số chưa đọc; thông báo mới tới chuông không cần tải lại trang")
	void newNotificationsArriveOverTheStream() throws Exception {
		UUID user = UUID.randomUUID();
		MvcResult stream = open(user);
		assertThat(body(stream)).contains("event:unread").contains("\"count\":0");

		transactions.executeWithoutResult(tx -> notifications.notify(user, NotificationType.TICKET_ISSUED,
				"Vé của bạn đã sẵn sàng", "2 vé", "/me/tickets", UUID.randomUUID()));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(body(stream))
				.contains("event:notification").contains("Vé của bạn đã sẵn sàng").contains("\"count\":1"));
	}

	@Test
	@DisplayName("AC-7: user chỉ nhận thông báo của mình")
	void usersOnlyReceiveTheirOwnNotifications() throws Exception {
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		MvcResult aliceStream = open(alice);
		MvcResult bobStream = open(bob);

		transactions.executeWithoutResult(tx -> notifications.notify(alice, NotificationType.TICKET_ISSUED,
				"Chỉ cho Alice", "2 vé", "/me/tickets", UUID.randomUUID()));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(body(aliceStream)).contains("Chỉ cho Alice"));
		assertThat(body(bobStream)).doesNotContain("Chỉ cho Alice").doesNotContain("event:notification");
	}

	@Test
	@DisplayName("Thông báo trùng (cùng event nguồn) không đẩy lần hai")
	void duplicateNotificationsAreNotPushedTwice() throws Exception {
		UUID user = UUID.randomUUID();
		UUID source = UUID.randomUUID();
		MvcResult stream = open(user);

		for (int i = 0; i < 2; i++) {
			transactions.executeWithoutResult(tx -> notifications.notify(user, NotificationType.TICKET_ISSUED,
					"Chỉ một lần", "2 vé", "/me/tickets", source));
		}

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(body(stream)).contains("Chỉ một lần"));
		assertThat(body(stream).split("event:notification", -1)).hasSize(2);
	}

	// ---- Hỗ trợ ----

	private MvcResult open(UUID user) throws Exception {
		return mockMvc.perform(get("/api/v1/me/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.AUTHORIZATION, TestTokens.token().subject(user).bearer()))
				.andExpect(request().asyncStarted()).andReturn();
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
