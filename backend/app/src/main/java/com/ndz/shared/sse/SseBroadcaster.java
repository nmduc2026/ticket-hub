package com.ndz.shared.sse;

import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phát sự kiện SSE tới mọi pod qua Redis pub/sub (overview.md §9, §12) và chuyển tin nhận được cho {@link SseHub} của
 * pod này. Pub/sub không lưu tin: client nối lại phải tải lại trạng thái đầy đủ (snapshot) rồi nghe tiếp.
 */
public class SseBroadcaster implements MessageListener {

	private final StringRedisTemplate redis;
	private final ObjectMapper json;
	private final SseHub hub;

	public SseBroadcaster(StringRedisTemplate redis, ObjectMapper json, SseHub hub) {
		this.redis = redis;
		this.json = json;
		this.hub = hub;
	}

	public void publish(String channel, String event, Object data) {
		publish(channel, event, data, false);
	}

	/** Phát rồi đóng kết nối của người nhận (sự kiện cuối, vd. phòng chờ đã đóng). */
	public void publishLast(String channel, String event, Object data) {
		publish(channel, event, data, true);
	}

	private void publish(String channel, String event, Object data, boolean last) {
		redis.convertAndSend(channel, json.writeValueAsString(new SseEnvelope(event, json.writeValueAsString(data), last)));
	}

	/**
	 * Phát sau khi transaction đang chạy commit, để client không nhận thay đổi rồi đọc lại thấy dữ liệu cũ hoặc thấy
	 * thay đổi bị hoàn tác. Không có transaction thì phát ngay.
	 */
	public void publishAfterCommit(String channel, String event, Object data) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			publish(channel, event, data);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				publish(channel, event, data);
			}
		});
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), java.nio.charset.StandardCharsets.UTF_8);
		hub.deliver(channel, json.readValue(message.getBody(), SseEnvelope.class));
	}

}
