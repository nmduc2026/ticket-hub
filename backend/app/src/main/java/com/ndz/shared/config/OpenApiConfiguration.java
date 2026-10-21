package com.ndz.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JsonNode;

/** OpenAPI do springdoc sinh ra là hợp đồng duy nhất cho frontend (Orval sinh client từ đây). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	static {
		// JsonNode (JSON tự do như hình học khu ghế) không được suy ra từ getter của Jackson: các getter của nó làm
		// schema vừa vô nghĩa vừa đổi giữa các lần chạy, nên hợp đồng lệch ngẫu nhiên. Khai là object tự do.
		SpringDocUtils.getConfig().replaceWithSchema(JsonNode.class, new ObjectSchema());
	}

	@Bean
	OpenAPI ticketHubOpenApi() {
		return new OpenAPI().info(new Info().title("Ticket Hub API").version("v1"));
	}

}
