# 0011. Realtime: Server-Sent Events

**Trạng thái:** Accepted

## Bối cảnh
Cần đẩy vị trí trong phòng chờ và trạng thái ghế xuống trình duyệt theo thời gian thực. Dữ liệu chỉ đi một chiều server → client; hành động của user (giữ ghế, thanh toán) đã đi qua REST.

## Các phương án
- **WebSocket / STOMP:** hai chiều, nhưng phải thêm giao thức, xử lý xác thực riêng và cấu hình proxy / load balancer.
- **SSE:** HTTP thường, đi qua proxy dễ, trình duyệt tự kết nối lại.

## Quyết định
**SSE** (`SseEmitter`) chạy trên virtual threads.
- Fan-out nhiều pod: thay đổi ghế publish lên Redis channel `seat-updates:{eventId}`, pod nào giữ kết nối thì đẩy xuống client của nó; không cần sticky session.
- Heartbeat 15 giây để giữ kết nối qua proxy.
- Redis Pub/Sub không lưu message nên không phát lại theo `Last-Event-ID`: kết nối lại thì client tải snapshot `GET /events/{id}/seats` rồi nghe tiếp.
- Phòng chờ: kết nối SSE không gia hạn session, trang phòng chờ gọi `GET /me` mỗi 5 phút (ADR 0027).

## Hệ quả
- (+) Đơn giản, đi qua BFF và cookie session như request thường.
- (+) Virtual threads giữ hàng nghìn kết nối không tốn platform thread.
- (−) Mỗi lần kết nối lại phải tải lại snapshot; không có lịch sử message.

Liên quan: `overview.md` §7.1, §9, §12, ADR 0001, 0027.
