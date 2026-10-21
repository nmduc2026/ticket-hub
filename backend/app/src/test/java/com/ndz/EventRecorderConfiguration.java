package com.ndz;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Ghi lại mọi event giữa module mà ứng dụng phát, kể cả event phát từ luồng bất đồng bộ của listener (nơi
 * {@code @RecordApplicationEvents} không với tới). Test tự {@link EventRecorder#clear()} trước khi chạy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EventRecorderConfiguration {

	@Bean
	EventRecorder eventRecorder() {
		return new EventRecorder();
	}

	public static class EventRecorder {

		private final List<IntegrationEvent> events = new CopyOnWriteArrayList<>();

		@EventListener
		void on(IntegrationEvent event) {
			events.add(event);
		}

		public void clear() {
			events.clear();
		}

		public <T extends IntegrationEvent> List<T> of(Class<T> type) {
			return events.stream().filter(type::isInstance).map(type::cast).toList();
		}

	}

}
