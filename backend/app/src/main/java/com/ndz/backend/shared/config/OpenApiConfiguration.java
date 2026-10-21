package com.ndz.backend.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI do springdoc sinh ra là hợp đồng duy nhất cho frontend (Orval sinh client từ đây). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	@Bean
	OpenAPI ticketHubOpenApi() {
		return new OpenAPI().info(new Info().title("Ticket Hub API").version("v1"));
	}

}
