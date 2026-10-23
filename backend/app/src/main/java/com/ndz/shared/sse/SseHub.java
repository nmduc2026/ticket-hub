package com.ndz.shared.sse;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Các kết nối SSE đang mở trên pod này, nhóm theo topic (overview.md §12). Pod khác không với tới kết nối của pod này,
 * nên thay đổi đi qua Redis pub/sub ({@link SseBroadcaster}) tới {@link #deliver} của mọi pod. Một kết nối có thể
 * nghe nhiều topic (vd. kênh chung của sự kiện và kênh riêng của user).
 */
public class SseHub implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(SseHub.class);

	private record Subscriber(SseEmitter emitter, Object attachment) {
	}

	/**
	 * Dừng trước khi web server bắt đầu chờ request đang chạy (graceful shutdown): kết nối SSE sống hàng chục phút,
	 * không đóng thì pod phải chờ hết thời hạn tắt. Phase lớn hơn nghĩa là dừng sớm hơn.
	 */
	private static final int PHASE = Integer.MAX_VALUE - 512;

	private final ConcurrentMap<String, Set<Subscriber>> byTopic = new ConcurrentHashMap<>();
	private volatile boolean running;

	/**
	 * Mở kết nối không hết hạn phía server (client tự đóng, heartbeat phát hiện kết nối chết).
	 *
	 * @param attachment dữ liệu của người mở để job cục bộ biết kết nối này của ai (xem {@link #forEach})
	 * @param topics các kênh kết nối này nhận
	 */
	public SseEmitter open(Object attachment, String... topics) {
		SseEmitter emitter = new SseEmitter(0L);
		Subscriber subscriber = new Subscriber(emitter, attachment);
		for (String topic : topics) {
			byTopic.computeIfAbsent(topic, key -> ConcurrentHashMap.newKeySet()).add(subscriber);
		}
		Runnable cleanup = () -> remove(subscriber, topics);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(error -> cleanup.run());
		return emitter;
	}

	/** Gửi tới các kết nối đang nghe {@code topic} trên pod này; {@code last} thì đóng kết nối sau khi gửi. */
	public void deliver(String topic, SseEnvelope message) {
		Set<Subscriber> subscribers = byTopic.get(topic);
		if (subscribers == null) {
			return;
		}
		for (Subscriber subscriber : List.copyOf(subscribers)) {
			if (send(subscriber, SseEmitter.event().name(message.event()).data(message.data())) && message.last()) {
				subscriber.emitter().complete();
			}
		}
	}

	/** Gửi một sự kiện riêng cho đúng một kết nối (vd. trạng thái ban đầu khi vừa mở). */
	public static void sendTo(SseEmitter emitter, String event, String data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data));
		}
		catch (IOException | IllegalStateException e) {
			emitter.completeWithError(e);
		}
	}

	/** Dòng chú thích định kỳ để proxy không cắt kết nối rảnh và để phát hiện kết nối đã chết. */
	public void heartbeat() {
		byTopic.values().stream().flatMap(Set::stream).distinct()
				.forEach(subscriber -> send(subscriber, SseEmitter.event().comment("heartbeat")));
	}

	/** Duyệt các kết nối có {@code attachment} thuộc kiểu {@code type} (mỗi kết nối một lần). */
	public <T> void forEach(Class<T> type, BiConsumer<T, SseEmitter> action) {
		byTopic.values().stream().flatMap(Set::stream).distinct()
				.filter(subscriber -> type.isInstance(subscriber.attachment()))
				.forEach(subscriber -> action.accept(type.cast(subscriber.attachment()), subscriber.emitter()));
	}

	/** Đóng mọi kết nối của pod này; trình duyệt tự nối lại và được chuyển sang pod khác (overview.md §17). */
	public void closeAll() {
		byTopic.values().stream().flatMap(Set::stream).distinct().forEach(subscriber -> subscriber.emitter().complete());
		byTopic.clear();
	}

	@Override
	public void start() {
		running = true;
	}

	@Override
	public void stop() {
		running = false;
		log.info("Tắt pod: đóng {} kết nối SSE", connections());
		closeAll();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		return PHASE;
	}

	public int connections() {
		return (int) byTopic.values().stream().flatMap(Set::stream).distinct().count();
	}

	private boolean send(Subscriber subscriber, SseEmitter.SseEventBuilder event) {
		try {
			subscriber.emitter().send(event);
			return true;
		}
		catch (IOException | IllegalStateException e) {
			// Client đã đi: kết nối không còn dùng được, dọn nó.
			log.debug("Kết nối SSE đã đóng, bỏ: {}", e.toString());
			subscriber.emitter().completeWithError(e);
			return false;
		}
	}

	private void remove(Subscriber subscriber, String[] topics) {
		for (String topic : topics) {
			byTopic.computeIfPresent(topic, (key, subscribers) -> {
				subscribers.remove(subscriber);
				return subscribers.isEmpty() ? null : subscribers;
			});
		}
	}

}
