package com.ndz.shared.config;

import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import com.ndz.shared.sse.SseBroadcaster;
import com.ndz.shared.sse.SseChannels;
import com.ndz.shared.sse.SseHub;

/** SSE realtime: hub kết nối của pod này và cầu nối Redis pub/sub tới các pod khác (overview.md §12). */
@Configuration(proxyBeanMethods = false)
public class SseConfiguration {

	@Bean
	SseHub sseHub() {
		return new SseHub();
	}

	@Bean
	SseBroadcaster sseBroadcaster(StringRedisTemplate redis, ObjectMapper json, SseHub hub) {
		return new SseBroadcaster(redis, json, hub);
	}

	@Bean
	RedisMessageListenerContainer sseListenerContainer(RedisConnectionFactory connectionFactory,
			SseBroadcaster broadcaster) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		SseChannels.patterns().forEach(pattern -> container.addMessageListener(broadcaster, new PatternTopic(pattern)));
		return container;
	}

	@Bean
	SseHeartbeat sseHeartbeat(SseHub hub) {
		return new SseHeartbeat(hub);
	}

	/** Heartbeat 15 giây cho mọi kết nối của pod này (overview.md §12). */
	static class SseHeartbeat {

		private final SseHub hub;

		SseHeartbeat(SseHub hub) {
			this.hub = hub;
		}

		@Scheduled(fixedDelayString = "${tickethub.sse.heartbeat-interval:15s}")
		void beat() {
			hub.heartbeat();
		}

	}

}
