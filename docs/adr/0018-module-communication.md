# 0018. Giao tiếp giữa module

**Trạng thái:** Accepted

## Bối cảnh
Module không được đọc bảng hay class nội bộ của nhau (P1). Thiết kế ban đầu cho Payment giữ projection `payable_bookings` dựng từ event `BookingCreated`:
- Khách nhận `201` rồi bấm "Thanh toán" ngay, Payment chưa nhận event → 404.
- Kafka chậm hoặc chết → không ai trả tiền được, booking hết hạn hàng loạt.

## Các phương án
- **Projection bất đồng bộ cho mọi dữ liệu**, trả `409 BOOKING_NOT_READY` + `Retry-After` cho FE tự thử lại: giữ thuần event, nhưng bước người dùng đang chờ vẫn phụ thuộc độ trễ event.
- **Event cho việc nền, gọi đồng bộ qua interface công khai khi người dùng đang chờ kết quả.**

## Quyết định
- Mặc định giao tiếp bằng **event**. Giai đoạn modular monolith dùng Spring Modulith in-process (outbox `event_publication`, ADR 0004); Kafka vào ở Sprint 8.
- Khi người dùng đang chờ kết quả thì **gọi đồng bộ interface công khai** của module kia. Ví dụ: Payment hỏi Booking lúc tạo PaymentIntent; Booking lấy danh sách ghế từ Catalog lúc publish (ADR 0021).
- Event là Java record mang sẵn `eventId` (UUIDv7) và `occurredAt`, để sau map 1-1 sang envelope Kafka (ADR 0005) mà không phải sửa consumer.
- Consumer phải idempotent (`processed_events`).

## Hệ quả
- (+) Đường thanh toán không phụ thuộc Kafka: Kafka chết thì vẫn giữ ghế và trả tiền được, chỉ vé và email đến trễ.
- (+) Ranh giới module giữ nguyên; khi tách service, đổi phần cài đặt của interface thành lời gọi API nội bộ (client `tickethub-internal`).
- (−) Có phụ thuộc đồng bộ Payment → Booking: Booking chết thì không tạo được thanh toán.

Liên quan: `overview.md` §4.2 (P1, P2), §6.2, §7.2, §8.3, §15.2, ADR 0004, 0012, 0021.
