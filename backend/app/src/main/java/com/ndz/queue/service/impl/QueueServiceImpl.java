package com.ndz.queue.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.api.CatalogApi.QueueEventInfo;
import com.ndz.queue.client.RedisWaitingRoom;
import com.ndz.queue.client.RedisWaitingRoom.Meta;
import com.ndz.queue.config.QueueProperties;
import com.ndz.queue.dto.response.QueueStatusResponse;
import com.ndz.queue.dto.response.QueueStatusResponse.Phase;
import com.ndz.queue.service.QueueService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.sse.SseBroadcaster;
import com.ndz.shared.sse.SseChannels;

@Service
class QueueServiceImpl implements QueueService {

	private static final Set<String> CLOSED_STATUSES = Set.of("SUSPENDED", "CANCELLED", "ENDED");

	private final RedisWaitingRoom room;
	private final CatalogApi catalog;
	private final QueueProperties properties;
	private final SseBroadcaster broadcaster;
	private final Clock clock;

	QueueServiceImpl(RedisWaitingRoom room, CatalogApi catalog, QueueProperties properties,
			SseBroadcaster broadcaster, Clock clock) {
		this.room = room;
		this.catalog = catalog;
		this.properties = properties;
		this.broadcaster = broadcaster;
		this.clock = clock;
	}

	@Override
	public QueueStatusResponse join(UUID eventId, UUID userId) {
		Meta meta = requireMeta(eventId);
		Instant now = clock.instant();
		if (now.isBefore(meta.saleStartsAt().minus(properties.openBefore()))) {
			throw new BusinessException(ErrorCode.QUEUE_NOT_OPEN, "Phòng chờ chưa mở");
		}
		room.markOpen(eventId);
		long rank = room.join(eventId, userId, score(meta, now));
		if (rank == RedisWaitingRoom.ALREADY_ADMITTED) {
			return admitted(eventId, userId);
		}
		return queued(meta, now, rank);
	}

	@Override
	public QueueStatusResponse status(UUID eventId, UUID userId) {
		Meta meta = requireMeta(eventId);
		if (room.admission(eventId, userId).isPresent()) {
			return admitted(eventId, userId);
		}
		return room.rank(eventId, userId).stream().mapToObj(rank -> queued(meta, clock.instant(), rank)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.QUEUE_NOT_JOINED, "Bạn chưa vào phòng chờ"));
	}

	@Override
	public void registerEvent(UUID eventId, boolean highDemand, Instant saleStartsAt) {
		room.writeMeta(eventId, new Meta(highDemand, saleStartsAt));
	}

	@Override
	public void close(UUID eventId, CloseReason reason) {
		broadcaster.publishLast(SseChannels.queue(eventId), "closed", Map.of("reason", reason.name()));
		room.close(eventId);
	}

	/**
	 * BR-04: vào trước giờ mở bán thì nhận số ngẫu nhiên trong {@code [0, sale_starts_at)}, nên luôn đứng trước người
	 * vào sau giờ mở bán (score = thời điểm vào, không nhỏ hơn {@code sale_starts_at}).
	 */
	private double score(Meta meta, Instant now) {
		if (now.isBefore(meta.saleStartsAt())) {
			return ThreadLocalRandom.current().nextDouble(0, meta.saleStartsAt().toEpochMilli());
		}
		return now.toEpochMilli();
	}

	private QueueStatusResponse admitted(UUID eventId, UUID userId) {
		return new QueueStatusResponse(null, null, Phase.ADMITTED, room.admission(eventId, userId).orElse(null));
	}

	private QueueStatusResponse queued(Meta meta, Instant now, long rank) {
		if (now.isBefore(meta.saleStartsAt())) {
			return new QueueStatusResponse(null, null, Phase.LOTTERY, null);
		}
		int position = Math.toIntExact(rank) + 1;
		long waitSeconds = Math.ceilDiv(position, properties.admitPerSecond());
		return new QueueStatusResponse(position, waitSeconds, Phase.QUEUED, null);
	}

	/**
	 * Lấy thông tin sự kiện từ Redis; thiếu (sự kiện publish trước khi có Queue, Redis mất dữ liệu, hoặc phòng chờ vừa
	 * đóng) thì hỏi Catalog qua interface công khai rồi ghi lại, vì khách đang chờ kết quả (ADR 0018).
	 */
	private Meta requireMeta(UUID eventId) {
		Meta meta = room.meta(eventId).orElseGet(() -> recoverMeta(eventId));
		if (!meta.highDemand()) {
			throw new BusinessException(ErrorCode.QUEUE_NOT_ENABLED, "Sự kiện không dùng phòng chờ");
		}
		return meta;
	}

	private Meta recoverMeta(UUID eventId) {
		QueueEventInfo info = catalog.queueInfo(eventId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
		if (CLOSED_STATUSES.contains(info.status())) {
			throw new BusinessException(ErrorCode.QUEUE_CLOSED, "Phòng chờ đã đóng");
		}
		Meta meta = new Meta(info.highDemand(), info.saleStartsAt());
		room.writeMeta(eventId, meta);
		return meta;
	}

}
