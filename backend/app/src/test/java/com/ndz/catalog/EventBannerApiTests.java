package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ndz.IntegrationTest;
import com.ndz.TestTokens;

/** Upload ảnh banner (plan Sprint 2 việc 3): lưu được và xem lại được; sai loại / quá cỡ bị 422; quyền sở hữu. */
@IntegrationTest
class EventBannerApiTests {

	/** PNG 1x1 hợp lệ. */
	private static final byte[] PNG = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAwS2OUAAAAABJRU5ErkJggg==");
	private static final byte[] JPEG = concat(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 },
			"JFIF\0".getBytes(), new byte[64]);
	private static final byte[] WEBP = concat("RIFF".getBytes(), new byte[] { 40, 0, 0, 0 }, "WEBPVP8 ".getBytes(),
			new byte[32]);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private UUID eventId;

	@BeforeEach
	void createEvent() {
		cleanUp();
		eventId = UUID.randomUUID();
		jdbc.update("insert into catalog.events (id, organizer_id, title) values (?, ?, 'Có banner')", eventId,
				TestTokens.ORGANIZER_ID);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from catalog.events where title = 'Có banner'");
	}

	@Test
	void uploadedImageIsStoredAndCanBeFetchedBack() throws Exception {
		String url = upload(PNG, "banner.png", "image/png", organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.bannerUrl").isNotEmpty()).andReturn().getResponse().getContentAsString();

		String bannerUrl = JsonPath.read(url, "$.bannerUrl");
		HttpResponse<byte[]> fetched = http.send(HttpRequest.newBuilder(URI.create(bannerUrl)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(fetched.statusCode()).isEqualTo(200);
		assertThat(fetched.body()).isEqualTo(PNG);
		assertThat(fetched.headers().firstValue("Content-Type")).hasValue("image/png");
		assertThat(bannerUrl).contains("/banners/" + eventId + "/").endsWith(".png");
	}

	@Test
	void jpegAndWebpAreAccepted() throws Exception {
		upload(JPEG, "a.jpg", "image/jpeg", organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.bannerUrl").value(org.hamcrest.Matchers.endsWith(".jpg")));
		upload(WEBP, "a.webp", "image/webp", organizer()).andExpect(status().isOk())
				.andExpect(jsonPath("$.bannerUrl").value(org.hamcrest.Matchers.endsWith(".webp")));
	}

	@Test
	void replacingTheBannerRemovesTheOldFile() throws Exception {
		String first = bannerUrlOf(upload(PNG, "one.png", "image/png", organizer()));
		String second = bannerUrlOf(upload(JPEG, "two.jpg", "image/jpeg", organizer()));

		assertThat(first).isNotEqualTo(second);
		assertThat(statusOf(first)).isEqualTo(404);
		assertThat(statusOf(second)).isEqualTo(200);
	}

	@Test
	void fileWithImageExtensionButOtherContentIsRejected() throws Exception {
		upload("không phải ảnh".getBytes(), "fake.png", "image/png", organizer())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
	}

	@Test
	void gifIsNotSupported() throws Exception {
		upload(concat("GIF89a".getBytes(), new byte[32]), "a.gif", "image/gif", organizer())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
	}

	@Test
	void imageLargerThanTwoMegabytesIsRejected() throws Exception {
		byte[] big = concat(PNG, new byte[2 * 1024 * 1024]);

		upload(big, "big.png", "image/png", organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
	}

	@Test
	void imageOfExactlyTwoMegabytesIsAccepted() throws Exception {
		byte[] exact = Arrays.copyOf(PNG, 2 * 1024 * 1024);

		upload(exact, "exact.png", "image/png", organizer()).andExpect(status().isOk());
	}

	@Test
	void emptyFileIsRejected() throws Exception {
		upload(new byte[0], "empty.png", "image/png", organizer()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMPTY_FILE"));
	}

	@Test
	void anotherOrganizerCannotUploadToMyEventAndCustomerIsForbidden() throws Exception {
		String stranger = TestTokens.token().subject(UUID.randomUUID()).roles("CUSTOMER", "ORGANIZER").bearer();

		upload(PNG, "a.png", "image/png", stranger).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		upload(PNG, "a.png", "image/png", TestTokens.token().subject(UUID.randomUUID()).roles("CUSTOMER").bearer())
				.andExpect(status().isForbidden());
	}

	@Test
	void bannerCanBeChangedAfterPublishButNotWhenCancelled() throws Exception {
		jdbc.update("""
				update catalog.events set status = 'PUBLISHED', venue_id = '00000000-0000-7000-8000-0000000b0001',
				    seat_map_id = '00000000-0000-7000-8000-0000000c0001', starts_at = now() + interval '30 days',
				    ends_at = now() + interval '31 days', sale_starts_at = now(), platform_fee_bps = 500 where id = ?""",
				eventId);
		upload(PNG, "a.png", "image/png", organizer()).andExpect(status().isOk());

		jdbc.update("update catalog.events set status = 'CANCELLED' where id = ?", eventId);
		upload(PNG, "b.png", "image/png", organizer()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_LOCKED"));
	}

	// ---- helpers ----

	private ResultActions upload(byte[] content, String filename, String contentType, String bearer) throws Exception {
		return mockMvc.perform(multipart("/api/v1/organizer/events/" + eventId + "/banner")
				.file(new MockMultipartFile("file", filename, contentType, content))
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.MULTIPART_FORM_DATA));
	}

	private static String bannerUrlOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				"$.bannerUrl");
	}

	private int statusOf(String url) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding())
				.statusCode();
	}

	private static String organizer() {
		return TestTokens.token().subject(TestTokens.ORGANIZER_ID).username("organizer").roles("CUSTOMER", "ORGANIZER")
				.bearer();
	}

	private static byte[] concat(byte[]... parts) {
		int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
		byte[] result = new byte[length];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, result, offset, part.length);
			offset += part.length;
		}
		return result;
	}

}
