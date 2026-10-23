package com.ndz.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.entity.AccountProcessedEvent;
import com.ndz.account.entity.AccountProcessedEvent.AccountProcessedEventId;

/** Khử trùng consumer (overview.md §8.3): ghi message đã xử lý trong cùng transaction với xử lý nghiệp vụ. */
public interface AccountProcessedEventRepository extends JpaRepository<AccountProcessedEvent, AccountProcessedEventId> {

	@Transactional
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO account.processed_events (consumer, message_id) VALUES (:consumer, :messageId)
			ON CONFLICT (consumer, message_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("consumer") String consumer, @Param("messageId") UUID messageId);

	/**
	 * Đánh dấu {@code (consumer, messageId)} là đã xử lý.
	 *
	 * @return true nếu đây là lần đầu (cứ xử lý tiếp); false nếu đã xử lý trước đó (bỏ qua)
	 */
	default boolean markProcessed(String consumer, UUID messageId) {
		return insertIfAbsent(consumer, messageId) == 1;
	}

}
