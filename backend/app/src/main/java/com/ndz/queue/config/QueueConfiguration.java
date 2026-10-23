package com.ndz.queue.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ndz.queue.client.RedisWaitingRoom;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QueueProperties.class)
class QueueConfiguration {

	@Bean
	RedisWaitingRoom redisWaitingRoom(StringRedisTemplate redis) {
		return new RedisWaitingRoom(redis);
	}

}
