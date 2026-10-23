package com.ndz.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.IntegrationTest;
import com.ndz.MailpitApi;
import com.ndz.MutableClock;
import com.ndz.TestTokens;
import com.ndz.booking.event.BookingHoldExpiring;
import com.ndz.booking.event.RefundCompleted;
import com.ndz.booking.event.RefundFailed;
import com.ndz.booking.event.RefundRequested;
import com.ndz.notification.entity.enums.NotificationType;
import com.ndz.notification.service.InAppNotificationService;
import com.ndz.ticket.event.TicketIssued;

/**
 * Thông báo trong app (plan Sprint 4 việc 6b; AC-14; overview.md §6.5, §11.2): tạo từ event, idempotent theo event
 * nguồn, chỉ chủ nhân đọc được, cài đặt theo loại với loại bắt buộc không tắt được, job xoá bản quá 90 ngày.
 */
@IntegrationTest
class InAppNotificationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	InAppNotificationService service;

	@Autowired
	MailpitApi mailpit;

	@Autowired
	MutableClock clock;

	private final UUID buyer = UUID.randomUUID();
	private final UUID someoneElse = UUID.randomUUID();

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notification.notifications");
		jdbc.update("delete from notification.notification_preferences");
		jdbc.update("delete from notification.processed_events");
		jdbc.update("delete from notification.notification_log");
	}

	// ---- AC-14 ----

	@Test
	@DisplayName("AC-14: phát vé tạo đúng 1 thông báo 'vé đã phát' cho người mua; gửi lại cùng event không tạo thêm")
	void ticketIssuedCreatesExactlyOneNotificationEvenWhenRedelivered() throws Exception {
		TicketIssued event = ticketIssued(buyer, 2);

		publish(event);
		publish(event);
		publish(event);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(buyer)).isEqualTo(1));
		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
				.untilAsserted(() -> assertThat(count(buyer)).isEqualTo(1));
		awaitEmailLogged(buyer);
		list(buyer, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].type").value("TICKET_ISSUED"))
				.andExpect(jsonPath("$.items[0].title").value("Vé của bạn đã sẵn sàng"))
				.andExpect(jsonPath("$.items[0].body").value("Sự kiện thử: 2 vé. Mở mã QR để vào cổng."))
				.andExpect(jsonPath("$.items[0].link").value("/me/tickets"))
				.andExpect(jsonPath("$.items[0].readAt").doesNotExist());
		mailpit.clear();
	}

	@Test
	@DisplayName("AC-14: yêu cầu hoàn tạo 1 thông báo kèm số tiền nhận lại; hoàn xong và hoàn lỗi mỗi loại 1 thông báo")
	void refundEventsEachCreateOneNotification() throws Exception {
		publish(new RefundRequested(UUID.randomUUID(), NOW, UUID.randomUUID(), buyer, UUID.randomUUID(), "Sự kiện thử",
				"a@b.c", RefundRequested.CUSTOMER_REQUEST, 950_000, "VND", null));
		publish(new RefundCompleted(UUID.randomUUID(), NOW, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				buyer, 950_000, "VND", "a@b.c", "Sự kiện thử"));
		publish(new RefundFailed(UUID.randomUUID(), NOW, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), buyer,
				950_000, "VND", "card_declined", "Sự kiện thử"));

		// Payment không có payment cho booking giả này nên tự phát thêm RefundFailed; chỉ cần mỗi loại có thông báo.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForList(
				"select distinct type from notification.notifications where user_id = ?", String.class, buyer))
				.containsExactlyInAnyOrder("REFUND_COMPLETED", "REFUND_FAILED", "REFUND_REQUESTED"));
		awaitEmailLogged(buyer);
		assertThat(count("select count(*) from notification.notifications where type = 'REFUND_REQUESTED'")).isEqualTo(1);
		assertThat(count("select count(*) from notification.notifications where type = 'REFUND_COMPLETED'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select body from notification.notifications where type = 'REFUND_REQUESTED'",
				String.class)).contains("950.000 ₫");
	}

	@Test
	@DisplayName("AC-7: khách yêu cầu hoàn → organizer của sự kiện nhận đúng 1 thông báo; gửi lại event không tạo thêm")
	void organizerIsToldOnceWhenACustomerRequestsARefund() throws Exception {
		UUID organizer = UUID.randomUUID();
		RefundRequested requested = new RefundRequested(UUID.randomUUID(), NOW, UUID.randomUUID(), buyer,
				UUID.randomUUID(), "Sự kiện thử", "a@b.c", RefundRequested.CUSTOMER_REQUEST, 950_000, "VND", organizer);

		publish(requested);
		publish(requested);

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(organizer)).isEqualTo(1));
		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
				.untilAsserted(() -> assertThat(count(organizer)).isEqualTo(1));
		list(organizer, "").andExpect(jsonPath("$.items[0].type").value("CUSTOMER_REFUND_REQUESTED"))
				.andExpect(jsonPath("$.items[0].link").value("/organizer/orders"));
	}

	@Test
	@DisplayName("AC-7: BookingHoldExpiring → khách nhận 1 thông báo trong app và 1 email; tắt cờ email thì chỉ còn thông báo trong app")
	void holdExpiringRespectsTheEmailFlag() throws Exception {
		String withEmail = "hold-" + UUID.randomUUID() + "@tickethub.local";
		String withoutEmail = "hold-" + UUID.randomUUID() + "@tickethub.local";
		mockMvc.perform(put("/api/v1/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(someoneElse))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"preferences":[{"type":"HOLD_EXPIRING","email":false,"inApp":true}]}""")).andExpect(status().isOk());
		BookingHoldExpiring mine = holdExpiring(buyer, withEmail);

		publish(mine);
		publish(mine);
		publish(holdExpiring(someoneElse, withoutEmail));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(count(buyer)).isEqualTo(1);
			assertThat(count(someoneElse)).isEqualTo(1);
			assertThat(mailpit.countTo(withEmail)).isEqualTo(1);
		});
		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
				.untilAsserted(() -> assertThat(mailpit.countTo(withEmail)).isEqualTo(1));
		assertThat(mailpit.countTo(withoutEmail)).isZero();
		assertThat(mailpit.subjectsTo(withEmail)).containsExactly("Sắp hết giờ giữ ghế của bạn");
		list(buyer, "").andExpect(jsonPath("$.items[0].type").value("HOLD_EXPIRING"))
				.andExpect(jsonPath("$.items[0].link").value("/checkout/" + mine.bookingId()));
		mailpit.clear();
	}

	@Test
	@DisplayName("Hoàn tự động vì thanh toán đến muộn (không có người nhận) không tạo thông báo")
	void autoRefundWithoutARecipientCreatesNothing() throws Exception {
		publish(new RefundCompleted(UUID.randomUUID(), NOW, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
				1_000_000, "VND", null, null));

		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
				.untilAsserted(() -> assertThat(count("select count(*) from notification.notifications")).isZero());
	}

	@Test
	@DisplayName("AC-14: GET /me/notifications của user khác không thấy thông báo này; không đọc / đánh dấu được")
	void otherUsersCannotSeeOrTouchMyNotifications() throws Exception {
		service.notify(buyer, NotificationType.TICKET_ISSUED, "Vé", "Nội dung", "/me/tickets", UUID.randomUUID());
		UUID id = jdbc.queryForObject("select id from notification.notifications", UUID.class);

		list(someoneElse, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(someoneElse)))
				.andExpect(jsonPath("$.count").value(0));
		markRead(someoneElse, id).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

		assertThat(jdbc.queryForObject("select read_at from notification.notifications", Timestamp.class)).isNull();
		list(buyer, "").andExpect(jsonPath("$.items.length()").value(1));
	}

	// ---- Số chưa đọc, đánh dấu đã đọc, phân trang ----

	@Test
	@DisplayName("AC-15: số chưa đọc, đánh dấu đã đọc từng cái và 'đọc tất cả' cập nhật số; đọc lại cùng cái vẫn 204")
	void unreadCountFollowsMarkReadAndReadAll() throws Exception {
		for (int i = 0; i < 3; i++) {
			service.notify(buyer, NotificationType.TICKET_ISSUED, "Vé " + i, "Nội dung", null, UUID.randomUUID());
		}
		assertThat(unread(buyer)).isEqualTo(3);

		UUID first = jdbc.queryForList("select id from notification.notifications order by id", UUID.class).get(0);
		markRead(buyer, first).andExpect(status().isNoContent());
		markRead(buyer, first).andExpect(status().isNoContent());
		assertThat(unread(buyer)).isEqualTo(2);
		list(buyer, "&unread=true").andExpect(jsonPath("$.items.length()").value(2));

		mockMvc.perform(post("/api/v1/me/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isNoContent());
		assertThat(unread(buyer)).isZero();
		list(buyer, "&unread=true").andExpect(jsonPath("$.items.length()").value(0));
		list(buyer, "").andExpect(jsonPath("$.items.length()").value(3));
	}

	@Test
	@DisplayName("Phân trang theo con trỏ: mới nhất trước, trang cuối không có nextCursor")
	void cursorPaginationWalksNewestFirstWithoutGapsOrRepeats() throws Exception {
		try {
			for (int i = 0; i < 5; i++) {
				clock.setInstant(NOW.plusSeconds(i)); // thời điểm phân biệt: đồng hồ hệ thống có thể trùng nhau
				service.notify(buyer, NotificationType.TICKET_ISSUED, "Vé " + i, "Nội dung", null, UUID.randomUUID());
			}
		}
		finally {
			clock.reset();
		}

		String first = list(buyer, "&size=2").andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].title").value("Vé 4")).andExpect(jsonPath("$.items[1].title").value("Vé 3"))
				.andReturn().getResponse().getContentAsString();
		String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.nextCursor");
		String second = list(buyer, "&size=2&cursor=" + cursor).andExpect(jsonPath("$.items[0].title").value("Vé 2"))
				.andExpect(jsonPath("$.items[1].title").value("Vé 1")).andReturn().getResponse().getContentAsString();
		String cursor2 = com.jayway.jsonpath.JsonPath.read(second, "$.nextCursor");
		list(buyer, "&size=2&cursor=" + cursor2).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Vé 0")).andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	@Test
	void withoutLoginIs401() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/notification-preferences")).andExpect(status().isUnauthorized());
	}

	// ---- Cài đặt ----

	@Test
	@DisplayName("AC-15: tắt loại 'Tạm dừng bán' thì không nhận loại đó; vẫn nhận loại khác")
	void disablingAnOptionalTypeStopsThatTypeOnly() throws Exception {
		savePreferences(buyer, """
				{"preferences":[{"type":"SALE_PAUSED","email":false,"inApp":false}]}""").andExpect(status().isOk());

		boolean paused = service.notify(buyer, NotificationType.SALE_PAUSED, "Tạm dừng bán", "x", null, UUID.randomUUID());
		boolean expiring = service.notify(buyer, NotificationType.HOLD_EXPIRING, "Sắp hết giờ", "x", null,
				UUID.randomUUID());

		assertThat(paused).isFalse();
		assertThat(expiring).isTrue();
		assertThat(jdbc.queryForList("select type from notification.notifications", String.class))
				.containsExactly("HOLD_EXPIRING");
		mockMvc.perform(get("/api/v1/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.type=='SALE_PAUSED')].inApp").value(false))
				.andExpect(jsonPath("$[?(@.type=='SALE_PAUSED')].mandatory").value(false))
				.andExpect(jsonPath("$[?(@.type=='HOLD_EXPIRING')].inApp").value(true))
				.andExpect(jsonPath("$[?(@.type=='TICKET_ISSUED')].mandatory").value(true));
	}

	@Test
	@DisplayName("AC-15: loại vé và hoàn tiền / huỷ sự kiện không tắt được: 422 NOTIFICATION_MANDATORY và không lưu gì")
	void mandatoryTypesCannotBeDisabled() throws Exception {
		savePreferences(buyer, """
				{"preferences":[{"type":"SALE_PAUSED","email":false,"inApp":false},
				                {"type":"REFUND_COMPLETED","email":true,"inApp":false}]}""")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NOTIFICATION_MANDATORY"));
		savePreferences(buyer, """
				{"preferences":[{"type":"TICKET_ISSUED","email":false,"inApp":true}]}""")
				.andExpect(status().isUnprocessableContent());
		savePreferences(buyer, """
				{"preferences":[{"type":"EVENT_CANCELLED","email":true,"inApp":false}]}""")
				.andExpect(status().isUnprocessableContent());

		assertThat(count("select count(*) from notification.notification_preferences")).isZero();
		assertThat(service.notify(buyer, NotificationType.REFUND_COMPLETED, "Đã hoàn", "x", null, UUID.randomUUID()))
				.isTrue();
	}

	@Test
	void unknownTypeIs422() throws Exception {
		savePreferences(buyer, """
				{"preferences":[{"type":"NO_SUCH_TYPE","email":true,"inApp":true}]}""")
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("UNKNOWN_NOTIFICATION_TYPE"));
	}

	@Test
	void preferencesAreSavedPerUser() throws Exception {
		savePreferences(buyer, """
				{"preferences":[{"type":"HOLD_EXPIRING","email":false,"inApp":true}]}""").andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(someoneElse)))
				.andExpect(jsonPath("$[?(@.type=='HOLD_EXPIRING')].email").value(true));
		mockMvc.perform(get("/api/v1/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(jsonPath("$[?(@.type=='HOLD_EXPIRING')].email").value(false));
	}

	// ---- Job xoá bản cũ ----

	@Test
	@DisplayName("§6.5: job xoá thông báo cũ hơn 90 ngày, giữ bản mới hơn")
	void purgeRemovesOnlyNotificationsOlderThanNinetyDays() {
		Instant now = Instant.now();
		insertAt(now.minus(Duration.ofDays(91)), "Quá cũ");
		insertAt(now.minus(Duration.ofDays(89)), "Còn hạn");
		insertAt(now.minus(Duration.ofDays(1)), "Mới");

		int deleted = service.purgeExpired();

		assertThat(deleted).isEqualTo(1);
		assertThat(jdbc.queryForList("select title from notification.notifications order by title", String.class))
				.containsExactly("Còn hạn", "Mới");
	}

	// ---- helpers ----

	/** Email cùng event được gửi bất đồng bộ; chờ nó ghi nhật ký để dọn dẹp không để lại dòng cho test khác. */
	private void awaitEmailLogged(UUID user) {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				count("select count(*) from notification.notification_log where user_id = '" + user + "'"))
				.isPositive());
	}

	private void publish(Object event) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(event));
	}

	private static BookingHoldExpiring holdExpiring(UUID user, String email) {
		return new BookingHoldExpiring(UUID.randomUUID(), NOW, UUID.randomUUID(), user, UUID.randomUUID(), "Sự kiện thử",
				email, NOW.plus(Duration.ofMinutes(2)));
	}

	private TicketIssued ticketIssued(UUID user, int tickets) {
		List<TicketIssued.Ticket> items = new java.util.ArrayList<>();
		for (int i = 0; i < tickets; i++) {
			items.add(new TicketIssued.Ticket(UUID.randomUUID(), "A", "B", i + 1, "VIP"));
		}
		return new TicketIssued(UUID.randomUUID(), NOW, UUID.randomUUID(), user, UUID.randomUUID(),
				"inapp-" + UUID.randomUUID() + "@tickethub.local", "Sự kiện thử", NOW.plus(Duration.ofDays(30)), "Arena",
				items);
	}

	private String bearer(UUID user) {
		return TestTokens.token().subject(user).bearer();
	}

	private ResultActions list(UUID user, String query) throws Exception {
		return mockMvc.perform(get("/api/v1/me/notifications?x=1" + query).header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private ResultActions markRead(UUID user, UUID id) throws Exception {
		return mockMvc.perform(post("/api/v1/me/notifications/" + id + "/read").header(HttpHeaders.AUTHORIZATION,
				bearer(user)));
	}

	private ResultActions savePreferences(UUID user, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private int unread(UUID user) throws Exception {
		String body = mockMvc.perform(get("/api/v1/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION,
				bearer(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.count");
	}

	private int count(UUID user) {
		return count("select count(*) from notification.notifications where user_id = '" + user + "'");
	}

	private int count(String sql) {
		Integer value = jdbc.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

	private void insertAt(Instant createdAt, String title) {
		jdbc.update("""
				insert into notification.notifications (id, user_id, type, title, body, source_event_id, created_at)
				values (?, ?, 'TICKET_ISSUED', ?, 'x', ?, ?)
				""", UUID.randomUUID(), buyer, title, UUID.randomUUID(), Timestamp.from(createdAt));
	}

}
