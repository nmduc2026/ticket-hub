package com.ndz.shared.sse;

/**
 * Một sự kiện SSE đi qua Redis pub/sub tới mọi pod.
 *
 * @param event tên sự kiện SSE ({@code event:})
 * @param data nội dung đã là JSON ({@code data:})
 * @param last đóng kết nối sau khi gửi (vd. phòng chờ đã đóng)
 */
public record SseEnvelope(String event, String data, boolean last) {
}
