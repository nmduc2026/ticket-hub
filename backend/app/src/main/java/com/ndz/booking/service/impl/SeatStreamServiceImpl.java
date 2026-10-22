package com.ndz.booking.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ndz.booking.repository.EventSnapshotRepository;
import com.ndz.booking.service.SeatStreamService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.sse.SseChannels;
import com.ndz.shared.sse.SseHub;

@Service
class SeatStreamServiceImpl implements SeatStreamService {

	private final EventSnapshotRepository snapshots;
	private final SseHub hub;

	SeatStreamServiceImpl(EventSnapshotRepository snapshots, SseHub hub) {
		this.snapshots = snapshots;
		this.hub = hub;
	}

	@Override
	public SseEmitter open(UUID eventId) {
		if (!snapshots.exists(eventId)) {
			throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện");
		}
		return hub.open(eventId, SseChannels.seats(eventId));
	}

}
