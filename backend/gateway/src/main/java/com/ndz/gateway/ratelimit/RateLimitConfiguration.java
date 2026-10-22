package com.ndz.gateway.ratelimit;

import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Rate limit dùng chung client Lettuce của Spring Data Redis, nên theo cùng cấu hình Redis (kể cả Sentinel) với
 * session. {@link RateLimitFilter} không khai báo là bean: Boot sẽ tự đăng ký mọi bean {@code Filter} vào chuỗi servlet
 * ngoài Spring Security; filter được thêm trực tiếp vào chuỗi bảo mật ở {@code SecurityConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

	@Bean
	RateLimiter rateLimiter(RedisConnectionFactory connectionFactory, RateLimitProperties properties,
			MeterRegistry metrics) {
		RedisClient client = (RedisClient) ((LettuceConnectionFactory) connectionFactory).getRequiredNativeClient();
		return new RateLimiter(client, properties, metrics);
	}

}
