package com.ndz.backend.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	/** Mọi code cần thời gian inject {@link Clock} để test điều khiển được giờ. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
