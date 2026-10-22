package com.ndz.queue.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;

import com.ndz.queue.client.RedisWaitingRoom;
import com.ndz.queue.client.RedisWaitingRoom.Meta;
import com.ndz.queue.config.QueueProperties;
import com.ndz.queue.service.QueueAdmissionService;

@Service
class QueueAdmissionServiceImpl implements QueueAdmissionService {

	private final RedisWaitingRoom room;
	private final QueueProperties properties;
	private final Clock clock;
	private final Counter admitted;

	QueueAdmissionServiceImpl(RedisWaitingRoom room, QueueProperties properties, Clock clock, MeterRegistry metrics) {
		this.room = room;
		this.properties = properties;
		this.clock = clock;
		this.admitted = Counter.builder("tickethub.queue.admitted").description("Người được gọi vào từ phòng chờ")
				.register(metrics);
	}

	@Override
	public int admitWaiting() {
		Instant now = clock.instant();
		int total = 0;
		for (UUID eventId : room.openEvents()) {
			Optional<Meta> meta = room.meta(eventId);
			if (meta.isEmpty() || !meta.get().highDemand()) {
				// Phòng chờ đã đóng (meta bị xoá) hoặc Redis mất dữ liệu: không còn gì để điều tiết.
				room.markClosed(eventId);
				continue;
			}
			// BR-04: trước giờ mở bán không ai được gọi vào.
			if (now.isBefore(meta.get().saleStartsAt())) {
				continue;
			}
			List<UUID> users = room.admit(eventId, now.toEpochMilli(), properties.maxActive(),
					properties.admitPerSecond(), properties.admissionTtl());
			total += users.size();
		}
		admitted.increment(total);
		return total;
	}

}
