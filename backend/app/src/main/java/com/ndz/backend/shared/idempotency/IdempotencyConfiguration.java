package com.ndz.backend.shared.idempotency;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
class IdempotencyConfiguration {

	@Bean
	IdempotencyFilter idempotencyFilter(
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
			IdempotencyStore store, IdempotencyProperties properties, Clock clock) {
		return new IdempotencyFilter(handlerMapping, store, properties, clock);
	}

	/** Filter được gắn vào chuỗi Spring Security (sau xác thực); tắt đăng ký servlet tự động để không chạy hai lần. */
	@Bean
	FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
		FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

}
