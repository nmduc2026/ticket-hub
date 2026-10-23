package com.ndz.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import com.ndz.IntegrationTest;

/**
 * OpenAPI là hợp đồng cho frontend (overview.md §11.1). Test kiểm hợp đồng hiện tại và ghi bản chuẩn ra
 * {@code frontend/openapi/openapi.json} để Orval sinh client; file đổi thì phải commit cùng thay đổi API.
 */
@IntegrationTest
class OpenApiContractTests {

	private static final Path CONTRACT_FILE = Path.of("..", "..", "frontend", "openapi", "openapi.json");

	@Autowired
	MockMvc mockMvc;

	@Test
	void contractDescribesTheMeEndpointWithProblemDetailsForUnauthenticated() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.operationId").value("getMe"))
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['401'].content['application/problem+json']")
						.exists())
				.andExpect(jsonPath("$.components.schemas.MeResponse.properties.roles").exists());
	}

	@Test
	void writesTheContractForTheFrontendGenerator() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
		JsonMapper mapper = JsonMapper.builder().build();
		// Luôn LF để file giống nhau trên Windows và Linux (CI so sánh nội dung).
		String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
				.replace("\r\n", "\n") + "\n";

		Files.createDirectories(CONTRACT_FILE.getParent());
		if (!Files.exists(CONTRACT_FILE) || !Files.readString(CONTRACT_FILE).equals(pretty)) {
			Files.writeString(CONTRACT_FILE, pretty, StandardCharsets.UTF_8);
		}

		assertThat(Files.readString(CONTRACT_FILE)).isEqualTo(pretty);
	}

}
