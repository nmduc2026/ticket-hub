package com.ndz.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ndz.notification.entity.enums.Channel;
import com.ndz.notification.entity.enums.LogStatus;

/**
 * Một lần gửi thông báo. Không lưu nội dung thư hay email người nhận (chỉ {@code userId}); lỗi ghi lý do ngắn.
 */
@Entity
@Table(schema = "notification", name = "notification_log")
public class NotificationLog {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	private Channel channel;

	@Column(name = "template_code", nullable = false, updatable = false)
	private String templateCode;

	@Column(name = "source_message_id", nullable = false, updatable = false)
	private UUID sourceMessageId;

	@Enumerated(EnumType.STRING)
	private LogStatus status;

	@Column(name = "sent_at")
	private Instant sentAt;

	private String error;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected NotificationLog() {
	}

	private NotificationLog(UUID id, UUID userId, Channel channel, String templateCode, UUID sourceMessageId,
			LogStatus status, Instant sentAt, String error, Instant now) {
		this.id = id;
		this.userId = userId;
		this.channel = channel;
		this.templateCode = templateCode;
		this.sourceMessageId = sourceMessageId;
		this.status = status;
		this.sentAt = sentAt;
		this.error = error;
		this.createdAt = now;
	}

	public static NotificationLog sent(UUID id, UUID userId, Channel channel, String templateCode,
			UUID sourceMessageId, Instant now) {
		return new NotificationLog(id, userId, channel, templateCode, sourceMessageId, LogStatus.SENT, now, null,
				now);
	}

	public static NotificationLog failed(UUID id, UUID userId, Channel channel, String templateCode,
			UUID sourceMessageId, String error, Instant now) {
		return new NotificationLog(id, userId, channel, templateCode, sourceMessageId, LogStatus.FAILED, null, error,
				now);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public LogStatus getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

}
