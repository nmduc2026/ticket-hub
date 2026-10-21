package com.ndz.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.ndz.IntegrationTest;

/**
 * Log là JSON một dòng mỗi bản ghi, có `traceId` (overview.md §16.1).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class JsonLoggingTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void logLineIsJsonWithTraceId(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/test-probe/log")).andReturn();

		String line = Arrays.stream(output.getAll().split("\\R"))
				.filter(l -> l.contains("probe-log-line"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Không thấy dòng log của request"));

		assertThat(line).startsWith("{");
		assertThat(JsonPath.<String>read(line, "$.message")).isEqualTo("probe-log-line");
		assertThat(JsonPath.<String>read(line, "$.traceId")).matches("[0-9a-f]{32}");
	}

}
