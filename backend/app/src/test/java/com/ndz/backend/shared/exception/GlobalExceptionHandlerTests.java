package com.ndz.backend.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ndz.backend.IntegrationTest;

/**
 * Lỗi trả về theo RFC 9457 kèm `code` nghiệp vụ (overview.md §11.1).
 */
@IntegrationTest
class GlobalExceptionHandlerTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void businessExceptionBecomesProblemDetailsWithBusinessCode() throws Exception {
		mockMvc.perform(get("/test-probe/business-error"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Ghế vừa có người giữ"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void businessExceptionWithoutStatusIsUnprocessable() throws Exception {
		mockMvc.perform(get("/test-probe/business-error-default-status"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MAX_SEATS_EXCEEDED"));
	}

	@Test
	void invalidBodyReturnsFieldErrors() throws Exception {
		mockMvc.perform(post("/test-probe/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "", "seats": ["a","b","c","d","e","f","g"]}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
				.andExpect(jsonPath("$.errors[?(@.field == 'seats')]").isNotEmpty());
	}

	@Test
	void unexpectedExceptionIsHiddenBehindAGenericProblem() throws Exception {
		mockMvc.perform(get("/test-probe/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("Đã xảy ra lỗi hệ thống"))
				.andExpect(jsonPath("$.traceId").isNotEmpty())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("chi tiết nội bộ"))));
	}

	@Test
	void springMvcErrorsAlsoCarryACode() throws Exception {
		mockMvc.perform(get("/test-probe/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

}
