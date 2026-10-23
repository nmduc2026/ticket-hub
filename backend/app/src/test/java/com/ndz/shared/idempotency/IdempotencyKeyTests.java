package com.ndz.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ndz.IdempotencyProbeController;
import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/**
 * Idempotency-Key (overview.md §6.7, ADR 0025): cùng key → cùng response; khác body → 422; đang xử lý → 409; key theo
 * người dùng; 5xx không bị lưu; bản ghi hết hạn được dọn.
 */
@IntegrationTest
class IdempotencyKeyTests {

	private static final String CREATE = "/api/v1/test-probe/idempotent/create";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	IdempotencyCleaner cleaner;

	private String bearer;
	private String key;

	@BeforeEach
	void setUp() {
		IdempotencyProbeController.reset();
		UUID user = UUID.randomUUID();
		bearer = TestTokens.token().subject(user).bearer();
		key = UUID.randomUUID().toString();
	}

	@Test
	void sameKeyAndBodyReturnsTheFirstResponseWithoutRunningTheControllerAgain() throws Exception {
		MockHttpServletResponse first = send(create(key, "{\"seats\":2}"));
		MockHttpServletResponse second = send(create(key, "{\"seats\":2}"));

		assertThat(first.getStatus()).isEqualTo(201);
		assertThat(second.getStatus()).isEqualTo(201);
		assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(JsonPath.<Integer>read(second.getContentAsString(), "$.execution")).isEqualTo(1);
		assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(1);
	}

	@Test
	void sameKeyWithDifferentBodyIsRejectedWith422() throws Exception {
		send(create(key, "{\"seats\":2}"));

		MockHttpServletResponse other = send(create(key, "{\"seats\":3}"));

		assertThat(other.getStatus()).isEqualTo(422);
		assertThat(other.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(JsonPath.<String>read(other.getContentAsString(), "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(1);
	}

	@Test
	void requestWhileTheFirstIsStillRunningGets409ThenReplayAfterItFinishes() throws Exception {
		IdempotencyProbeController.release = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<MockHttpServletResponse> first = pool.submit(() -> send(create(key, "{\"seats\":2}")));
			assertThat(IdempotencyProbeController.started.await(10, TimeUnit.SECONDS)).isTrue();

			MockHttpServletResponse duringProcessing = send(create(key, "{\"seats\":2}"));
			IdempotencyProbeController.release.countDown();
			MockHttpServletResponse firstResponse = first.get(10, TimeUnit.SECONDS);
			MockHttpServletResponse afterwards = send(create(key, "{\"seats\":2}"));

			assertThat(duringProcessing.getStatus()).isEqualTo(409);
			assertThat(JsonPath.<String>read(duringProcessing.getContentAsString(), "$.code"))
					.isEqualTo("REQUEST_IN_PROGRESS");
			assertThat(firstResponse.getStatus()).isEqualTo(201);
			assertThat(afterwards.getStatus()).isEqualTo(201);
			assertThat(afterwards.getContentAsString()).isEqualTo(firstResponse.getContentAsString());
			assertThat(IdempotencyProbeController.executions.get()).isEqualTo(1);
		}
		finally {
			IdempotencyProbeController.release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void manySimultaneousRequestsWithTheSameKeyRunTheControllerOnce() throws Exception {
		IdempotencyProbeController.release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> calls = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				calls.add(() -> send(create(key, "{\"seats\":2}")).getStatus());
			}
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> call : calls) {
				futures.add(pool.submit(call));
			}
			assertThat(IdempotencyProbeController.started.await(10, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(300);
			IdempotencyProbeController.release.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get(10, TimeUnit.SECONDS));
			}
			assertThat(statuses).allMatch(status -> status == 201 || status == 409);
			assertThat(statuses).filteredOn(status -> status == 201).isNotEmpty();
			assertThat(IdempotencyProbeController.executions.get()).isEqualTo(1);
		}
		finally {
			IdempotencyProbeController.release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void keyIsScopedPerUser() throws Exception {
		String otherUser = TestTokens.token().subject(UUID.randomUUID()).bearer();

		MockHttpServletResponse mine = send(create(key, "{\"seats\":2}"));
		MockHttpServletResponse theirs = send(createAs(otherUser, key, "{\"seats\":2}"));

		assertThat(mine.getStatus()).isEqualTo(201);
		assertThat(theirs.getStatus()).isEqualTo(201);
		assertThat(theirs.getHeader("Idempotent-Replayed")).isNull();
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(2);
	}

	@Test
	void businessErrorIsStoredAndReplayedAsProblemDetails() throws Exception {
		MockHttpServletResponse first = send(post("/api/v1/test-probe/idempotent/conflict")
				.header(HttpHeaders.AUTHORIZATION, bearer).header("Idempotency-Key", key));
		MockHttpServletResponse second = send(post("/api/v1/test-probe/idempotent/conflict")
				.header(HttpHeaders.AUTHORIZATION, bearer).header("Idempotency-Key", key));

		assertThat(first.getStatus()).isEqualTo(409);
		assertThat(second.getStatus()).isEqualTo(409);
		assertThat(second.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(JsonPath.<String>read(second.getContentAsString(), "$.code")).isEqualTo("SEAT_UNAVAILABLE");
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(1);
	}

	@Test
	void serverErrorIsNotStoredSoTheClientCanRetry() throws Exception {
		MockHttpServletResponse first = send(post("/api/v1/test-probe/idempotent/boom").header(HttpHeaders.AUTHORIZATION, bearer)
				.header("Idempotency-Key", key));
		MockHttpServletResponse retry = send(post("/api/v1/test-probe/idempotent/boom").header(HttpHeaders.AUTHORIZATION, bearer)
				.header("Idempotency-Key", key));

		assertThat(first.getStatus()).isEqualTo(500);
		assertThat(retry.getStatus()).isEqualTo(500);
		assertThat(retry.getHeader("Idempotent-Replayed")).isNull();
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(2);
	}

	@Test
	void missingOrMalformedKeyIsRejectedBeforeAnyBusinessWork() throws Exception {
		MockHttpServletResponse missing = send(post(CREATE).header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{}"));
		MockHttpServletResponse malformed = send(create("co khoang trang", "{}"));

		assertThat(missing.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<String>read(missing.getContentAsString(), "$.code")).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
		assertThat(malformed.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<String>read(malformed.getContentAsString(), "$.code")).isEqualTo("IDEMPOTENCY_KEY_INVALID");
		assertThat(IdempotencyProbeController.executions.get()).isZero();
	}

	@Test
	void endpointWithoutTheAnnotationNeedsNoKey() throws Exception {
		MockHttpServletResponse response = send(post("/api/v1/test-probe/idempotent/plain")
				.header(HttpHeaders.AUTHORIZATION, bearer));

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void expiredRecordIsClaimedAgain() throws Exception {
		send(create(key, "{\"seats\":2}"));
		jdbc.update("update shared.idempotency_keys set expires_at = ? where key = ?",
				Timestamp.from(Instant.now().minusSeconds(5)), key);

		MockHttpServletResponse again = send(create(key, "{\"seats\":2}"));

		assertThat(again.getStatus()).isEqualTo(201);
		assertThat(again.getHeader("Idempotent-Replayed")).isNull();
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(2);
	}

	@Test
	void abandonedInProgressRecordIsClaimedAgain() throws Exception {
		send(create(key, "{\"seats\":2}"));
		// Giả lập tiến trình chết giữa chừng: bản ghi kẹt IN_PROGRESS từ rất lâu.
		jdbc.update("""
				update shared.idempotency_keys set status = 'IN_PROGRESS', response_status = null, response_body = null,
				created_at = ? where key = ?""", Timestamp.from(Instant.now().minusSeconds(3600)), key);

		MockHttpServletResponse again = send(create(key, "{\"seats\":2}"));

		assertThat(again.getStatus()).isEqualTo(201);
		assertThat(IdempotencyProbeController.executions.get()).isEqualTo(2);
	}

	@Test
	void cleanerDeletesOnlyExpiredRecords() throws Exception {
		String keepKey = UUID.randomUUID().toString();
		send(create(key, "{\"seats\":2}"));
		send(create(keepKey, "{\"seats\":2}"));
		jdbc.update("update shared.idempotency_keys set expires_at = ? where key = ?",
				Timestamp.from(Instant.now().minusSeconds(5)), key);

		int deleted = cleaner.deleteExpired();

		assertThat(deleted).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from shared.idempotency_keys where key = ?", Integer.class, key))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from shared.idempotency_keys where key = ?", Integer.class,
				keepKey)).isEqualTo(1);
	}

	private MockHttpServletRequestBuilder create(String idempotencyKey, String body) {
		return createAs(bearer, idempotencyKey, body);
	}

	private MockHttpServletRequestBuilder createAs(String authorization, String idempotencyKey, String body) {
		return post(CREATE).header(HttpHeaders.AUTHORIZATION, authorization).header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andReturn().getResponse();
	}

}
