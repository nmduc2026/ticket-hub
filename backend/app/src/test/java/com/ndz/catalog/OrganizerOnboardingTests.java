package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.EventRecorderConfiguration.EventRecorder;
import com.ndz.IntegrationTest;
import com.ndz.KeycloakTestApi;
import com.ndz.TestTokens;
import com.ndz.account.service.OrganizerRoleService;
import com.ndz.catalog.event.OrganizerApplied;
import com.ndz.catalog.event.OrganizerApproved;
import com.ndz.catalog.event.OrganizerRejected;
import com.ndz.shared.util.Uuid7;

/**
 * Đăng ký và duyệt organizer (overview.md §7.6, §6.6) với Keycloak thật: máy trạng thái hồ sơ, quyền (chỉ ADMIN duyệt,
 * khách chỉ thấy hồ sơ của mình), gán role {@code ORGANIZER} idempotent. Map: AC-7, AC-8 của Sprint 5.
 */
@IntegrationTest
class OrganizerOnboardingTests {

	private static final String APPLY = "/api/v1/organizer/apply";
	private static final String APPLICATION = "/api/v1/organizer/application";
	private static final String ADMIN_ORGANIZERS = "/api/v1/admin/organizers";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EventRecorder events;

	@Autowired
	KeycloakTestApi keycloak;

	@Autowired
	OrganizerRoleService roleService;

	private UUID applicant;

	@BeforeEach
	void newApplicant() {
		events.clear();
		applicant = UUID.randomUUID();
	}

	@AfterEach
	void cleanUp() {
		// Chờ listener bất đồng bộ (gán role, thông báo) xong rồi mới dọn.
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
				jdbc.queryForObject("select count(*) from shared.event_publication where completion_date is null "
						+ "and event_type like 'com.ndz.catalog.event.Organizer%'",
						Integer.class)).isZero());
		jdbc.update("delete from catalog.organizers where submitted_at is not null");
	}

	// ---- Nộp hồ sơ ----

	@Test
	void applyCreatesPendingApplicationAndPublishesOrganizerApplied() throws Exception {
		apply(applicant, form("BUSINESS", "0312 345 678")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		assertThat(events.of(OrganizerApplied.class)).singleElement()
				.satisfies(event -> assertThat(event.organizerId()).isEqualTo(applicant));
		mockMvc.perform(get(APPLICATION).header(HttpHeaders.AUTHORIZATION, customer(applicant)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.displayName").value("Saigon Sound"))
				.andExpect(jsonPath("$.legalId").value("0312345678"))
				.andExpect(jsonPath("$.contactPhone").value("0908765432"));
	}

	@Test
	void applicationOfSomeoneWhoNeverAppliedIs404AndOthersCannotReadMine() throws Exception {
		mockMvc.perform(get(APPLICATION).header(HttpHeaders.AUTHORIZATION, customer(applicant)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());

		mockMvc.perform(get(APPLICATION).header(HttpHeaders.AUTHORIZATION, customer(UUID.randomUUID())))
				.andExpect(status().isNotFound());
	}

	@Test
	void applyingAgainWhilePendingIs409() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());

		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_PENDING"));
		assertThat(events.of(OrganizerApplied.class)).hasSize(1);
	}

	@Test
	void anExistingOrganizerCannotApplyAgain() throws Exception {
		apply(TestTokens.ORGANIZER_ID, form("BUSINESS", "0312345678")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ORGANIZER"));
	}

	@Test
	void legalIdMustMatchTheOrganizerType() throws Exception {
		apply(applicant, form("BUSINESS", "123456")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("LEGAL_ID_INVALID"));
		apply(applicant, form("INDIVIDUAL", "0312345678")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("LEGAL_ID_INVALID"));
		apply(applicant, form("INDIVIDUAL", "079 123 456 789")).andExpect(status().isCreated());
	}

	@Test
	void invalidPhoneOrWebsiteIs422AndMissingFieldsIs400() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678").replace("0908 765 432", "12345"))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PHONE_INVALID"));
		apply(applicant, form("BUSINESS", "0312345678").replace("\"website\": \"\"", "\"website\": \"saigonsound.vn\""))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("WEBSITE_INVALID"));
		apply(applicant, "{\"type\": \"BUSINESS\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void applyRequiresLoginAndOrdinaryAccountsOnlyListNothingForAdmin() throws Exception {
		mockMvc.perform(post(APPLY).contentType(MediaType.APPLICATION_JSON).content(form("BUSINESS", "0312345678")))
				.andExpect(status().isUnauthorized());
	}

	// ---- Duyệt và từ chối ----

	@Test
	void onlyAdminCanListApproveOrReject() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());

		mockMvc.perform(get(ADMIN_ORGANIZERS)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ADMIN_ORGANIZERS).header(HttpHeaders.AUTHORIZATION, customer(applicant)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(ADMIN_ORGANIZERS + "/" + applicant + "/approve").header(HttpHeaders.AUTHORIZATION,
				organizer())).andExpect(status().isForbidden());
		mockMvc.perform(post(ADMIN_ORGANIZERS + "/bulk-approve").header(HttpHeaders.AUTHORIZATION, customer(applicant))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [\"%s\"]}".formatted(applicant)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminListsApplicationsFilteredByStatusNewestFirst() throws Exception {
		UUID second = UUID.randomUUID();
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		apply(second, form("INDIVIDUAL", "079123456789")).andExpect(status().isCreated());
		jdbc.update("update catalog.organizers set status = 'REJECTED' where id = ?", second);

		mockMvc.perform(get(ADMIN_ORGANIZERS + "?status=PENDING").header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].id").value(applicant.toString()))
				.andExpect(jsonPath("$.items[0].email").value("khoa@saigonsound.vn"));
		// Không lọc: chỉ hồ sơ đã nộp, organizer có sẵn (không có hồ sơ) không lẫn vào.
		mockMvc.perform(get(ADMIN_ORGANIZERS).header(HttpHeaders.AUTHORIZATION, admin()))
				.andExpect(jsonPath("$.total").value(2));
	}

	@Test
	void rejectingNeedsAReasonAndTheApplicantSeesItAndCanApplyAgain() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());

		reject(applicant, "{\"reason\": \"\"}").andExpect(status().isBadRequest());
		reject(applicant, "{}").andExpect(status().isBadRequest());
		assertThat(statusOf(applicant)).isEqualTo("PENDING");

		reject(applicant, "{\"reason\": \"Ảnh giấy phép bị mờ\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(events.of(OrganizerRejected.class)).singleElement()
				.satisfies(event -> assertThat(event.reason()).isEqualTo("Ảnh giấy phép bị mờ"));
		mockMvc.perform(get(APPLICATION).header(HttpHeaders.AUTHORIZATION, customer(applicant)))
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").value("Ảnh giấy phép bị mờ"));

		// Bị từ chối thì nộp lại được: về PENDING và xoá lý do cũ.
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));
		mockMvc.perform(get(APPLICATION).header(HttpHeaders.AUTHORIZATION, customer(applicant)))
				.andExpect(jsonPath("$.rejectionReason").doesNotExist());
	}

	@Test
	void everyReviewWritesOneAuditRowWithTheAdminAndAnEnversRevision() throws Exception {
		UUID rejected = UUID.randomUUID();
		// Người được duyệt có thật trong Keycloak để việc gán role không lỗi và để lại event dở.
		applicant = keycloak.createUser("audit-" + Instant.now().toEpochMilli(), "audit@example.test");
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		apply(rejected, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		UUID approvedByAdmin = TestTokens.ADMIN_ID;

		reject(rejected, "{\"reason\": \"Thiếu giấy phép\"}").andExpect(status().isOk());
		approve(applicant).andExpect(status().isOk());

		assertThat(jdbc.queryForMap("""
				select actor_id, action, target_type, before_state ->> 'status' as before_status,
				       after_state ->> 'status' as after_status
				from shared.audit_log where target_id = ?
				""", applicant.toString())).containsEntry("actor_id", approvedByAdmin)
				.containsEntry("action", "ORGANIZER_APPROVED").containsEntry("target_type", "organizer")
				.containsEntry("before_status", "PENDING").containsEntry("after_status", "APPROVED");
		assertThat(jdbc.queryForObject("select after_state ->> 'reason' from shared.audit_log where target_id = ?",
				String.class, rejected.toString())).isEqualTo("Thiếu giấy phép");
		// Envers: một revision cho lúc tạo hồ sơ và một cho lúc duyệt, kèm người thao tác.
		assertThat(jdbc.queryForList("""
				select a.status from catalog.organizers_aud a where a.id = ? order by a.rev
				""", String.class, applicant)).containsExactly("PENDING", "APPROVED");
		assertThat(jdbc.queryForObject("""
				select r.actor_id from catalog.organizers_aud a join shared.revinfo r on r.rev = a.rev
				where a.id = ? and a.status = 'APPROVED'
				""", UUID.class, applicant)).isEqualTo(approvedByAdmin);
	}

	@Test
	void aRejectedOrForbiddenReviewWritesNoAuditRow() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());

		reject(applicant, "{\"reason\": \"\"}").andExpect(status().isBadRequest());
		mockMvc.perform(post(ADMIN_ORGANIZERS + "/" + applicant + "/approve").header(HttpHeaders.AUTHORIZATION,
				customer(applicant))).andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select count(*) from shared.audit_log where target_id = ?", Integer.class,
				applicant.toString())).isZero();
	}

	@Test
	void reviewingAnApplicationThatIsNotPendingIs409() throws Exception {
		apply(applicant, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		reject(applicant, "{\"reason\": \"Thiếu giấy phép\"}").andExpect(status().isOk());

		approve(applicant).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORGANIZER_NOT_PENDING"));
		reject(applicant, "{\"reason\": \"Lần nữa\"}").andExpect(status().isConflict());
		approve(UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORGANIZER_NOT_FOUND"));
	}

	// ---- Gán role (Keycloak thật) ----

	@Test
	void approvingGrantsTheOrganizerRoleInKeycloakAndInTheProfile() throws Exception {
		UUID user = keycloak.createUser("khoa-" + Instant.now().toEpochMilli(), "khoa@example.test");
		apply(user, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		assertThat(keycloak.realmRoles(user)).doesNotContain("ORGANIZER");

		approve(user).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

		assertThat(events.of(OrganizerApproved.class)).hasSize(1);
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(keycloak.realmRoles(user)).contains("ORGANIZER");
			assertThat(rolesInProfile(user)).contains("ORGANIZER");
		});
	}

	@Test
	void grantingTheRoleTwiceIsHarmless() throws Exception {
		UUID user = keycloak.createUser("lan-" + Instant.now().toEpochMilli(), "lan@example.test");
		apply(user, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		approve(user).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> assertThat(keycloak.realmRoles(user)).contains("ORGANIZER"));

		// Cùng một event gửi lại, và một event khác cho người đã có role: không lỗi, role vẫn đúng một bản.
		OrganizerApproved replay = events.of(OrganizerApproved.class).get(0);
		roleService.onOrganizerApproved(replay);
		roleService.onOrganizerApproved(new OrganizerApproved(Uuid7.next(java.time.Clock.systemUTC()), Instant.now(),
				user, "Saigon Sound", "khoa@saigonsound.vn"));

		assertThat(keycloak.realmRoles(user)).containsOnlyOnce("ORGANIZER");
		assertThat(rolesInProfile(user).stream().filter("ORGANIZER"::equals)).hasSize(1);
	}

	@Test
	void bulkApproveSkipsApplicationsThatAreNotPendingAndReportsThem() throws Exception {
		UUID first = keycloak.createUser("b1-" + Instant.now().toEpochMilli(), "b1@example.test");
		UUID second = keycloak.createUser("b2-" + Instant.now().toEpochMilli(), "b2@example.test");
		UUID rejected = keycloak.createUser("b3-" + Instant.now().toEpochMilli(), "b3@example.test");
		for (UUID user : List.of(first, second, rejected)) {
			apply(user, form("BUSINESS", "0312345678")).andExpect(status().isCreated());
		}
		reject(rejected, "{\"reason\": \"Thiếu giấy phép\"}").andExpect(status().isOk());
		UUID unknown = UUID.randomUUID();

		mockMvc.perform(post(ADMIN_ORGANIZERS + "/bulk-approve").header(HttpHeaders.AUTHORIZATION, admin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\": [\"%s\", \"%s\", \"%s\", \"%s\"]}".formatted(first, second, rejected, unknown)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.approved.length()").value(2))
				.andExpect(jsonPath("$.approved[0]").value(first.toString()))
				.andExpect(jsonPath("$.skipped.length()").value(2))
				.andExpect(jsonPath("$.skipped[0].id").value(rejected.toString()))
				.andExpect(jsonPath("$.skipped[0].code").value("ORGANIZER_NOT_PENDING"))
				.andExpect(jsonPath("$.skipped[1].code").value("ORGANIZER_NOT_FOUND"));

		assertThat(statusOf(first)).isEqualTo("APPROVED");
		assertThat(statusOf(rejected)).isEqualTo("REJECTED");
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(keycloak.realmRoles(first)).contains("ORGANIZER");
			assertThat(keycloak.realmRoles(second)).contains("ORGANIZER");
		});
	}

	// ---- Hỗ trợ ----

	private ResultActions apply(UUID user, String body) throws Exception {
		return mockMvc.perform(post(APPLY).header(HttpHeaders.AUTHORIZATION, customer(user))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions approve(UUID organizerId) throws Exception {
		return mockMvc.perform(post(ADMIN_ORGANIZERS + "/" + organizerId + "/approve")
				.header(HttpHeaders.AUTHORIZATION, admin()));
	}

	private ResultActions reject(UUID organizerId, String body) throws Exception {
		return mockMvc.perform(post(ADMIN_ORGANIZERS + "/" + organizerId + "/reject")
				.header(HttpHeaders.AUTHORIZATION, admin()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String statusOf(UUID organizerId) {
		return jdbc.queryForObject("select status from catalog.organizers where id = ?", String.class, organizerId);
	}

	private List<String> rolesInProfile(UUID userId) {
		return jdbc.queryForList("select unnest(roles) from account.user_profiles where user_id = ?", String.class,
				userId);
	}

	private static String customer(UUID user) {
		return TestTokens.token().subject(user).roles("CUSTOMER").bearer();
	}

	private static String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).roles("CUSTOMER", "ORGANIZER").bearer();
	}

	private static String admin() {
		return TestTokens.token().subject(TestTokens.ADMIN_ID).roles("CUSTOMER", "ADMIN").bearer();
	}

	private static String form(String type, String legalId) {
		return """
				{"type": "%s", "displayName": "Saigon Sound", "legalName": "Công ty TNHH Saigon Sound",
				 "legalId": "%s", "contactEmail": "khoa@saigonsound.vn", "contactPhone": "0908 765 432",
				 "website": "", "introduction": "Đơn vị tổ chức đêm nhạc indie tại TP.HCM", "eventScale": "500–2.000 khách"}
				""".formatted(type, legalId);
	}

}
