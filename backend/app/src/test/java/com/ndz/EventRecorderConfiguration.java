package com.ndz;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ndz.shared.event.IntegrationEvent;

/**
 * Ghi lại mọi event giữa module mà ứng dụng phát, kể cả event phát từ luồng bất đồng bộ của listener (nơi
 * {@code @RecordApplicationEvents} không với tới); event của transaction bị hoàn tác không được ghi. Test tự
 * {@link EventRecorder#clear()} trước khi chạy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EventRecorderConfiguration {

	@Bean
	EventRecorder eventRecorder() {
		return new EventRecorder();
	}

	public static class EventRecorder {

		private final List<IntegrationEvent> events = new CopyOnWriteArrayList<>();

		/**
		 * Dùng {@code @EventListener} thường rồi tự chờ commit, không dùng {@code @TransactionalEventListener}: Modulith
		 * ghi mọi listener giao dịch vào {@code event_publication}, sẽ làm lệch các test đếm bản ghi ở đó.
		 */
		@EventListener
		void on(IntegrationEvent event) {
			if (!TransactionSynchronizationManager.isSynchronizationActive()) {
				events.add(event);
				return;
			}
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					events.add(event);
				}
			});
		}

		public void clear() {
			events.clear();
		}

		public <T extends IntegrationEvent> List<T> of(Class<T> type) {
			return events.stream().filter(type::isInstance).map(type::cast).toList();
		}

	}

}
