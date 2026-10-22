package com.ndz.notification.service.impl;

import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ndz.notification.service.InAppNotificationService;
import com.ndz.notification.service.NotificationStreamService;
import com.ndz.shared.sse.SseChannels;
import com.ndz.shared.sse.SseHub;

@Service
class NotificationStreamServiceImpl implements NotificationStreamService {

	private final InAppNotificationService inbox;
	private final SseHub hub;
	private final ObjectMapper json;

	NotificationStreamServiceImpl(InAppNotificationService inbox, SseHub hub, ObjectMapper json) {
		this.inbox = inbox;
		this.hub = hub;
		this.json = json;
	}

	@Override
	public SseEmitter open(UUID userId) {
		SseEmitter emitter = hub.open(userId, SseChannels.userNotify(userId));
		SseHub.sendTo(emitter, "unread", json.writeValueAsString(Map.of("count", inbox.unreadCount(userId))));
		return emitter;
	}

}
