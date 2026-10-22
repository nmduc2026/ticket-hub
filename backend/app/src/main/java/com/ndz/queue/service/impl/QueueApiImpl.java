package com.ndz.queue.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ndz.queue.api.QueueApi;
import com.ndz.queue.client.RedisWaitingRoom;

/** Cài đặt {@link QueueApi} trong cùng process (giai đoạn modular monolith). */
@Service
class QueueApiImpl implements QueueApi {

	private final RedisWaitingRoom room;

	QueueApiImpl(RedisWaitingRoom room) {
		this.room = room;
	}

	@Override
	public void bookingPending(UUID eventId, UUID bookingId, Instant countedUntil) {
		room.addPending(eventId, bookingId, countedUntil);
	}

	@Override
	public void bookingSettled(UUID eventId, UUID bookingId) {
		room.removePending(eventId, bookingId);
	}

}
