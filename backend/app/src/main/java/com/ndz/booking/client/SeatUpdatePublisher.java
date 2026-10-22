package com.ndz.booking.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ndz.shared.sse.SseBroadcaster;
import com.ndz.shared.sse.SseChannels;

/**
 * Gom thay đổi trạng thái ghế rồi phát lên {@code seat-updates:{eventId}} (overview.md §9, §12). Thay đổi chỉ được ghi
 * nhận sau khi transaction đổi ghế commit; {@link #flush()} (job vài trăm mili giây một lần) gộp các thay đổi của cùng
 * sự kiện thành một tin, ghế đổi nhiều lần thì lấy trạng thái cuối. Gộp để lúc mở bán, hàng trăm lượt giữ ghế mỗi giây
 * không thành hàng triệu lần ghi xuống kết nối SSE. Pod chết trước khi {@code flush} thì mất tối đa vài trăm mili giây
 * thay đổi; client nối lại sẽ tải lại snapshot nên số liệu vẫn đúng ở lần tải kế.
 */
public class SeatUpdatePublisher {

	private record Change(UUID eventId, UUID seatId, String status) {
	}

	private final SseBroadcaster broadcaster;
	private final Queue<Change> pending = new ConcurrentLinkedQueue<>();

	public SeatUpdatePublisher(SseBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	/** @param status trạng thái mới của ghế: {@code AVAILABLE}, {@code HELD} hoặc {@code SOLD} */
	public void recordAfterCommit(UUID eventId, Collection<UUID> seatIds, String status) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			record(eventId, seatIds, status);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				record(eventId, seatIds, status);
			}
		});
	}

	private void record(UUID eventId, Collection<UUID> seatIds, String status) {
		seatIds.forEach(seatId -> pending.add(new Change(eventId, seatId, status)));
	}

	/** Phát mọi thay đổi đang chờ, mỗi sự kiện một tin. */
	public void flush() {
		Map<UUID, Map<UUID, String>> byEvent = new LinkedHashMap<>();
		for (Change change = pending.poll(); change != null; change = pending.poll()) {
			byEvent.computeIfAbsent(change.eventId(), key -> new LinkedHashMap<>()).put(change.seatId(), change.status());
		}
		byEvent.forEach((eventId, seats) -> {
			List<Map<String, Object>> changes = seats.entrySet().stream()
					.<Map<String, Object>>map(entry -> Map.of("seatId", entry.getKey(), "status", entry.getValue()))
					.toList();
			broadcaster.publish(SseChannels.seats(eventId), "seats", Map.of("changes", changes));
		});
	}

}
