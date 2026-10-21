package com.ndz.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.KeycloakTestApi;
import com.ndz.MailpitApi;
import com.ndz.TestTokens;
import com.ndz.catalog.event.OrganizerApplied;
import com.ndz.notification.service.InAppNotificationService;

/**
 * Thông báo theo vai trò khi duyệt hồ sơ organizer (overview.md §6.5, §7.6): Admin nhận "hồ sơ chờ duyệt", organizer
 * nhận kết quả trong app và qua email, idempotent theo event nguồn. Map: AC-7 của Sprint 5.
 */
@IntegrationTest
class OrganizerNotificationTests {

	private static final String DOMAIN = "@org-notify.test";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EventRecorder events;

	@Autowired
	KeycloakTestApi keycloak;

	@Autowired
	MailpitApi mailpit;

	@Autowired
	InAppNotificationService notifications;

	private UUID adminOne;
	private UUID adminTwo;
	private UUID lockedAdmin;

	@BeforeEach
	void admins() {
		events.clear();
		mailpit.clear();
		adminOne = adminProfile("one", true);
		adminTwo = adminProfile("two", true);
		lockedAdmin = adminProfile("locked", false);
	}

	@AfterEach
	void cleanUp() {
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
				"select count(*) from shared.event_publication where completion_date is null "
						+ "and event_type like 'com.ndz.catalog.event.Organizer%'",
				Integer.class)).isZero());
		jdbc.update("delete from notification.notifications where type like 'ORGANIZER_%'");
		jdbc.update("delete from catalog.organizers where submitted_at is not null");
		jdbc.update("delete from account.user_profiles where email like ?", "%" + DOMAIN);
	}

	@Test
	void everyActiveAdminGetsExactlyOnePendingNotificationAndReplayAddsNone() throws Exception {
		UUID applicant = UUID.randomUUID();

		apply(applicant, "ban-to-chuc" + DOMAIN).andExpect(status().isCreated());

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(count(adminOne, "ORGANIZER_APPLICATION_PENDING")).isEqualTo(1);
			assertThat(count(adminTwo, "ORGANIZER_APPLICATION_PENDING")).isEqualTo(1);
		});
		assertThat(count(lockedAdmin, "ORGANIZER_APPLICATION_PENDING")).isZero();

		// Gửi lại cùng event (Modulith gửi lại sau lỗi): không tạo trùng.
		OrganizerApplied replay = events.of(OrganizerApplied.class).get(0);
		notifications.onOrganizerApplied(replay);
		assertThat(count(adminOne, "ORGANIZER_APPLICATION_PENDING")).isEqualTo(1);
		assertThat(count(adminTwo, "ORGANIZER_APPLICATION_PENDING")).isEqualTo(1);
	}

	@Test
	void approvedOrganizerGetsAnInAppNotificationAndAnEmail() throws Exception {
		String email = "duoc-duyet" + DOMAIN;
		UUID organizer = keycloak.createUser("approved-" + Instant.now().toEpochMilli(), email);
		apply(organizer, email).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/admin/organizers/" + organizer + "/approve").header(HttpHeaders.AUTHORIZATION,
				admin())).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(count(organizer, "ORGANIZER_APPROVED")).isEqualTo(1);
			assertThat(mailpit.subjectsTo(email)).contains("Hồ sơ ban tổ chức của bạn đã được duyệt");
		});
		assertThat(mailpit.latestHtmlTo(email)).contains("Saigon Sound");
	}

	@Test
	void rejectedOrganizerSeesTheReasonInTheAppAndInTheEmail() throws Exception {
		String email = "bi-tu-choi" + DOMAIN;
		UUID organizer = UUID.randomUUID();
		apply(organizer, email).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/admin/organizers/" + organizer + "/reject")
				.header(HttpHeaders.AUTHORIZATION, admin()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Ảnh giấy phép bị mờ\"}")).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(count(organizer, "ORGANIZER_REJECTED")).isEqualTo(1);
			assertThat(mailpit.subjectsTo(email)).contains("Hồ sơ ban tổ chức của bạn chưa được duyệt");
		});
		assertThat(jdbc.queryForObject(
				"select body from notification.notifications where user_id = ? and type = 'ORGANIZER_REJECTED'",
				String.class, organizer)).isEqualTo("Ảnh giấy phép bị mờ");
		assertThat(mailpit.latestHtmlTo(email)).contains("Ảnh giấy phép bị mờ");
	}

	@Test
	void theResultNotificationsCannotBeSwitchedOffButThePendingOneCan() throws Exception {
		var types = notifications.preferences(adminOne);

		assertThat(types).filteredOn(item -> item.type().equals("ORGANIZER_APPROVED")).singleElement()
				.satisfies(item -> assertThat(item.mandatory()).isTrue());
		assertThat(types).filteredOn(item -> item.type().equals("ORGANIZER_APPLICATION_PENDING")).singleElement()
				.satisfies(item -> assertThat(item.mandatory()).isFalse());
	}

	private org.springframework.test.web.servlet.ResultActions apply(UUID user, String contactEmail)
			throws Exception {
		String body = """
				{"type": "BUSINESS", "displayName": "Saigon Sound", "legalName": "Công ty TNHH Saigon Sound",
				 "legalId": "0312345678", "contactEmail": "%s", "contactPhone": "0908765432",
				 "introduction": "Đơn vị tổ chức đêm nhạc indie"}
				""".formatted(contactEmail);
		return mockMvc.perform(post("/api/v1/organizer/apply").header(HttpHeaders.AUTHORIZATION,
				TestTokens.token().subject(user).roles("CUSTOMER").bearer()).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private UUID adminProfile(String name, boolean enabled) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into account.user_profiles (user_id, email, full_name, username, roles, enabled, created_at,
				                                   updated_at)
				values (?, ?, ?, ?, array['CUSTOMER', 'ADMIN'], ?, ?, ?)
				""", id, name + DOMAIN, "Admin " + name, name, enabled, Timestamp.from(Instant.now()),
				Timestamp.from(Instant.now()));
		return id;
	}

	private int count(UUID user, String type) {
		return jdbc.queryForObject("select count(*) from notification.notifications where user_id = ? and type = ?",
				Integer.class, user, type);
	}

	private static String admin() {
		return TestTokens.token().subject(TestTokens.ADMIN_ID).roles("CUSTOMER", "ADMIN").bearer();
	}

}
