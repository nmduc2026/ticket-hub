package com.ndz.queue.service.impl;

import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ndz.queue.dto.response.QueueStatusResponse;
import com.ndz.queue.dto.response.QueueStatusResponse.Phase;
import com.ndz.queue.service.QueueService;
import com.ndz.queue.service.QueueStreamService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.sse.SseChannels;
import com.ndz.shared.sse.SseHub;

@Service
class QueueStreamServiceImpl implements QueueStreamService {

	private static final Logger log = LoggerFactory.getLogger(QueueStreamServiceImpl.class);

	/** Kết nối của ai, để job đẩy vị trí biết gửi gì cho ai. */
	private record Subscriber(UUID eventId, UUID userId) {
	}

	private final QueueService queue;
	private final SseHub hub;
	private final ObjectMapper json;

	QueueStreamServiceImpl(QueueService queue, SseHub hub, ObjectMapper json) {
		this.queue = queue;
		this.hub = hub;
		this.json = json;
	}

	@Override
	public SseEmitter open(UUID eventId, UUID userId) {
		QueueStatusResponse status = queue.status(eventId, userId);
		SseEmitter emitter = hub.open(new Subscriber(eventId, userId), SseChannels.queue(eventId),
				SseChannels.queueUser(eventId, userId));
		send(emitter, status);
		return emitter;
	}

	@Override
	public void pushPositions() {
		hub.forEach(Subscriber.class, (subscriber, emitter) -> {
			try {
				send(emitter, queue.status(subscriber.eventId(), subscriber.userId()));
			}
			catch (BusinessException e) {
				// Phòng chờ đóng hoặc khách không còn trong hàng giữa hai nhịp: sự kiện closed / admitted đã báo rồi.
				log.debug("Bỏ qua đẩy vị trí cho {}: {}", subscriber, e.getMessage());
			}
		});
	}

	private void send(SseEmitter emitter, QueueStatusResponse status) {
		if (status.phase() == Phase.ADMITTED) {
			SseHub.sendTo(emitter, "admitted", json.writeValueAsString(Map.of("admissionExpiresAt", status.admissionExpiresAt())));
			return;
		}
		SseHub.sendTo(emitter, "position", json.writeValueAsString(status));
	}

}
