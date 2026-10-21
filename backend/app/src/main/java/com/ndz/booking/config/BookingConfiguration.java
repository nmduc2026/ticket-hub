package com.ndz.booking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ndz.booking.client.RedisSeatHolds;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ BookingProperties.class, SeatHoldProperties.class })
class BookingConfiguration {

	@Bean
	RedisSeatHolds redisSeatHolds(StringRedisTemplate redis) {
		return new RedisSeatHolds(redis);
	}

}
