package com.ndz.backend.catalog.internal.application;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.backend.catalog.internal.application.OrganizerEventService.EventDetail;
import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.ImageType;
import com.ndz.backend.catalog.internal.persistence.EventRepository;
import com.ndz.backend.shared.BusinessException;
import com.ndz.backend.shared.Uuid7;
import com.ndz.backend.shared.storage.ObjectStorage;
import com.ndz.backend.shared.storage.StorageException;
import com.ndz.backend.shared.storage.StorageProperties;

/**
 * Upload ảnh banner (jpg / png / webp, ≤ 2 MB). Gọi kho file ngoài transaction DB: ghi file trước, rồi mới cập nhật
 * địa chỉ trong một transaction ngắn; transaction lỗi thì xoá file vừa ghi, thành công thì xoá ảnh cũ.
 */
@Service
public class EventBannerService {

	private static final Logger log = LoggerFactory.getLogger(EventBannerService.class);

	private final EventRepository events;
	private final OrganizerEventService eventService;
	private final ObjectStorage storage;
	private final StorageProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	EventBannerService(EventRepository events, OrganizerEventService eventService, ObjectStorage storage,
			StorageProperties properties, TransactionTemplate transaction, Clock clock) {
		this.events = events;
		this.eventService = eventService;
		this.storage = storage;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
	}

	public EventDetail upload(UUID organizerId, UUID eventId, byte[] content) {
		ImageType type = validate(content);
		// Kiểm quyền sở hữu và trạng thái trước khi tốn công ghi file.
		transaction.executeWithoutResult(status -> owned(organizerId, eventId).requireDisplayEditable());

		String key = "banners/%s/%s.%s".formatted(eventId, Uuid7.next(clock), type.extension());
		storage.put(key, content, type.contentType());
		String previousUrl;
		try {
			previousUrl = transaction.execute(status -> {
				Event event = owned(organizerId, eventId);
				String previous = event.changeBanner(storage.publicUrl(key));
				events.save(event);
				return previous;
			});
		}
		catch (RuntimeException e) {
			removeQuietly(key, "ảnh vừa ghi sau khi cập nhật sự kiện thất bại");
			throw e;
		}
		String previousKey = storage.keyOf(previousUrl);
		if (previousKey != null) {
			removeQuietly(previousKey, "ảnh banner cũ");
		}
		return eventService.get(organizerId, eventId);
	}

	private ImageType validate(byte[] content) {
		if (content.length == 0) {
			throw new BusinessException("EMPTY_FILE", "File rỗng");
		}
		if (content.length > properties.maxBannerSize().toBytes()) {
			throw new BusinessException("IMAGE_TOO_LARGE",
					"Ảnh vượt quá " + properties.maxBannerSize().toMegabytes() + " MB");
		}
		return ImageType.detect(content).orElseThrow(
				() -> new BusinessException("UNSUPPORTED_IMAGE_TYPE", "Chỉ nhận ảnh jpg, png hoặc webp"));
	}

	private Event owned(UUID organizerId, UUID eventId) {
		return events.findByIdAndOrganizerId(eventId, organizerId).orElseThrow(
				() -> new BusinessException("EVENT_NOT_FOUND", "Không tìm thấy sự kiện", HttpStatus.NOT_FOUND));
	}

	/** Xoá không làm hỏng kết quả chính: file mồ côi chỉ tốn chỗ, nhưng phải để lại dấu vết trong log. */
	private void removeQuietly(String key, String what) {
		try {
			storage.delete(key);
		}
		catch (StorageException e) {
			log.warn("Không xoá được {} ({}); file còn lại trong kho", what, key, e);
		}
	}

}
