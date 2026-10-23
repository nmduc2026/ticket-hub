package com.ndz.notification.service;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Luồng SSE thông báo trong app của chính người dùng (overview.md §12). */
public interface NotificationStreamService {

	/**
	 * Mở luồng {@code notification {…}} (thông báo mới) và {@code unread {count}} (số chưa đọc). Gửi ngay
	 * {@code unread} hiện tại khi nối. Chỉ nhận thông báo của {@code userId}.
	 */
	SseEmitter open(UUID userId);

}
