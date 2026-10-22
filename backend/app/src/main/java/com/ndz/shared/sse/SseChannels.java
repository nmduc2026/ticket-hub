package com.ndz.shared.sse;

import java.util.List;
import java.util.UUID;

/** Tên kênh Redis pub/sub cho SSE (overview.md §9). Cũng là tên topic của {@link SseHub}. */
public final class SseChannels {

	private static final String SEATS = "seat-updates:";
	private static final String USER_NOTIFY = "user-notify:";
	private static final String QUEUE = "queue-stream:";

	private SseChannels() {
	}

	/** Các mẫu mà mỗi pod đăng ký nghe. */
	public static List<String> patterns() {
		return List.of(SEATS + "*", USER_NOTIFY + "*", QUEUE + "*");
	}

	/** Thay đổi trạng thái ghế của một sự kiện, cho mọi người đang xem sơ đồ. */
	public static String seats(UUID eventId) {
		return SEATS + eventId;
	}

	/** Thông báo mới và số chưa đọc của một user. */
	public static String userNotify(UUID userId) {
		return USER_NOTIFY + userId;
	}

	/** Phòng chờ của sự kiện, cho mọi người trong hàng (vd. đóng phòng chờ). */
	public static String queue(UUID eventId) {
		return QUEUE + eventId;
	}

	/** Phòng chờ của sự kiện, riêng một user (vị trí, được gọi vào). */
	public static String queueUser(UUID eventId, UUID userId) {
		return QUEUE + eventId + ":" + userId;
	}

}
